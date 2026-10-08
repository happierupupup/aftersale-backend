package com.yean.trade.ticket.job;

import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.enums.TicketStatus;
import com.yean.trade.ticket.mapper.TicketMapper;
import com.yean.trade.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 每天 2:30：创建超过 {@code timeoutHours}（默认 48h）仍未办结的工单自动关闭，并通知申请人。
 * 与每天 10:00 催办（{@link PendingRemindService}）独立；催办只提醒、不关单。
 * 跳过待用户收货（换货发货后可能已扣库存，Demo 不做补偿）。
 * 多实例锁 {@code lock:schedule:timeout:aftersale}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeoutCloseService {

    public static final String LOCK_KEY = "lock:schedule:timeout:aftersale";

    /** 待审核 / 待发货 / 待退款（不含待收货） */
    private static final List<Integer> TIMEOUT_STATUSES = Arrays.asList(
            TicketStatus.PENDING.getCode(),
            TicketStatus.WAIT_SHIP.getCode(),
            TicketStatus.WAIT_REFUND.getCode()
    );

    private final TicketMapper ticketMapper;
    private final TicketService ticketService;
    private final TimeoutCloseProperties properties;
    private final RedissonClient redissonClient;

    /**
     * @return 成功关单条数；未抢到锁或未启用返回 0
     */
    public int closeTimedOut() {
        if (!properties.isEnabled()) {
            log.info("timeout close disabled");
            return 0;
        }
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.MINUTES);
            if (!locked) {
                log.info("timeout close skipped, lock held by another instance");
                return 0;
            }
            return doClose();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("timeout close interrupted");
            return 0;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private int doClose() {
        int hours = Math.max(1, properties.getTimeoutHours());
        LocalDateTime before = LocalDateTime.now().minusHours(hours);
        List<Ticket> rows = ticketMapper.selectTimedOut(
                TIMEOUT_STATUSES, before, Math.max(1, properties.getScanLimit()));
        if (rows == null || rows.isEmpty()) {
            log.info("timeout close: no timed-out tickets, before={}", before);
            return 0;
        }
        int closed = 0;
        for (Ticket row : rows) {
            try {
                ticketService.timeoutReject(row.getAftersaleNo(),
                        "超时自动关闭（产生超过" + hours + "小时未办结）");
                closed++;
            } catch (RuntimeException e) {
                log.error("timeout close failed, aftersaleNo={}, err={}",
                        row.getAftersaleNo(), e.getMessage());
            }
        }
        log.info("timeout close done, scanned={}, closed={}, before={}", rows.size(), closed, before);
        return closed;
    }
}
