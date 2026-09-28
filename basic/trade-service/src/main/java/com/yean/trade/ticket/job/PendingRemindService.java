package com.yean.trade.ticket.job;

import com.yean.common.dto.notify.NotifySendDTO;
import com.yean.common.result.R;
import com.yean.common.rpc.NotifyFeignClient;
import com.yean.trade.notify.config.NotifyProperties;
import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.enums.TicketStatus;
import com.yean.trade.ticket.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 每天催办未处理售后：待审核→商家、待发货→仓库、待退款→商家。
 * 按接收人聚合一条站内信；多实例用 {@code lock:schedule:pending-remind} 防双跑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingRemindService {

    public static final String LOCK_KEY = "lock:schedule:pending-remind";

    private static final List<Integer> PENDING_STATUSES = Arrays.asList(
            TicketStatus.PENDING.getCode(),
            TicketStatus.WAIT_SHIP.getCode(),
            TicketStatus.WAIT_REFUND.getCode()
    );

    private final TicketMapper ticketMapper;
    private final NotifyFeignClient notifyFeignClient;
    private final NotifyProperties notifyProperties;
    private final PendingRemindProperties properties;
    private final RedissonClient redissonClient;

    /**
     * @return 成功发出的站内信条数；未抢到锁或未启用返回 0
     */
    public int remindPending() {
        if (!properties.isEnabled()) {
            log.info("pending remind disabled");
            return 0;
        }
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.MINUTES);
            if (!locked) {
                log.info("pending remind skipped, lock held by another instance");
                return 0;
            }
            return doRemind();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("pending remind interrupted");
            return 0;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private int doRemind() {
        List<Ticket> rows = ticketMapper.selectPendingByStatuses(PENDING_STATUSES, properties.getScanLimit());
        if (rows == null || rows.isEmpty()) {
            log.info("pending remind: no open tickets");
            return 0;
        }
        Map<Long, List<Ticket>> byUser = new LinkedHashMap<>();
        for (Ticket row : rows) {
            Long userId = resolveReceiver(row);
            if (userId == null) {
                log.warn("pending remind skip ticket without receiver, aftersaleNo={}, status={}",
                        row.getAftersaleNo(), row.getStatus());
                continue;
            }
            byUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(row);
        }
        int sent = 0;
        for (Map.Entry<Long, List<Ticket>> e : byUser.entrySet()) {
            try {
                R.unwrap(notifyFeignClient.send(buildNotify(e.getKey(), e.getValue())));
                sent++;
            } catch (RuntimeException ex) {
                log.error("pending remind notify failed, userId={}, err={}", e.getKey(), ex.getMessage());
            }
        }
        log.info("pending remind done, tickets={}, receivers={}, sent={}", rows.size(), byUser.size(), sent);
        return sent;
    }

    private Long resolveReceiver(Ticket row) {
        int status = row.getStatus() == null ? -1 : row.getStatus();
        if (status == TicketStatus.PENDING.getCode() || status == TicketStatus.WAIT_REFUND.getCode()) {
            return row.getMerchantId();
        }
        if (status == TicketStatus.WAIT_SHIP.getCode()) {
            return notifyProperties.getWarehouseUserId();
        }
        return null;
    }

    private NotifySendDTO buildNotify(Long userId, List<Ticket> tickets) {
        int preview = Math.max(1, properties.getPreviewLimit());
        String previewText = tickets.stream()
                .limit(preview)
                .map(t -> t.getAftersaleNo() + "（" + statusLabel(t.getStatus()) + "）")
                .collect(Collectors.joining("、"));
        String more = tickets.size() > preview ? " 等共 " + tickets.size() + " 笔" : "";
        NotifySendDTO dto = new NotifySendDTO();
        dto.setUserId(userId);
        dto.setTitle("售后待处理提醒");
        dto.setContent("您有 " + tickets.size() + " 笔售后待处理：" + previewText + more + "，请尽快处理。");
        dto.setChannel("INBOX");
        dto.setBizType("AFTERSALE");
        return dto;
    }

    private static String statusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        for (TicketStatus s : TicketStatus.values()) {
            if (s.getCode() == status) {
                return s.getLabel();
            }
        }
        return String.valueOf(status);
    }
}
