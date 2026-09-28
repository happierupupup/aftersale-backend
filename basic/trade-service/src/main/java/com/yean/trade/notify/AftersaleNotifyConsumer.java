package com.yean.trade.notify;

import com.yean.common.dto.notify.NotifySendDTO;
import com.yean.common.result.R;
import com.yean.common.rpc.NotifyFeignClient;
import com.yean.trade.notify.config.NotifyProperties;
import com.yean.trade.notify.dto.AftersaleNotifyMessage;
import com.yean.trade.ticket.enums.TicketAction;
import com.yean.trade.ticket.enums.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 消费售后通知 → portal 站内信。Redis 按 bizId 粗幂等，避免重复消费刷多条。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = AftersaleNotifyPublisher.TOPIC,
        consumerGroup = "aftersale-notify-consumer"
)
public class AftersaleNotifyConsumer implements RocketMQListener<AftersaleNotifyMessage> {

    private static final Duration DEDUP_TTL = Duration.ofDays(7);
    private static final String DEDUP_PREFIX = "notify:dedup:";

    private final NotifyFeignClient notifyFeignClient;
    private final NotifyProperties notifyProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(AftersaleNotifyMessage message) {
        if (message == null || !StringUtils.hasText(message.getBizId())) {
            log.warn("skip empty aftersale notify message");
            return;
        }
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(DEDUP_PREFIX + message.getBizId(), "1", DEDUP_TTL);
        if (Boolean.FALSE.equals(first)) {
            log.info("aftersale notify duplicated, skip bizId={}", message.getBizId());
            return;
        }
        try {
            for (NotifySendDTO dto : buildNotifyList(message)) {
                R.unwrap(notifyFeignClient.send(dto));
            }
            log.info("aftersale notify consumed, bizId={}", message.getBizId());
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(DEDUP_PREFIX + message.getBizId());
            throw e;
        }
    }

    private List<NotifySendDTO> buildNotifyList(AftersaleNotifyMessage msg) {
        TicketAction action = parseAction(msg.getEvent());
        String no = msg.getAftersaleNo();
        String sku = StringUtils.hasText(msg.getSkuName()) ? msg.getSkuName() : "商品";
        List<NotifySendDTO> list = new ArrayList<>(2);

        switch (action) {
            case APPLY:
                list.add(inbox(msg.getMerchantId(), "新售后待审核",
                        "售后单 " + no + "（" + sku + "）待您审核。"));
                break;
            case APPROVE:
                list.add(inbox(msg.getUserId(), "售后审核通过",
                        "您的售后单 " + no + " 已通过商家审核。"));
                if (msg.getStatus() != null && msg.getStatus() == TicketStatus.WAIT_SHIP.getCode()) {
                    list.add(inbox(notifyProperties.getWarehouseUserId(), "售后待仓库发货",
                            "售后单 " + no + "（" + sku + "）待发货。"));
                }
                break;
            case REJECT:
                list.add(inbox(msg.getUserId(), "售后已驳回",
                        "您的售后单 " + no + " 已被驳回。"));
                break;
            case WITHDRAW:
                list.add(inbox(msg.getMerchantId(), "用户已撤回售后",
                        "售后单 " + no + " 已被用户撤回。"));
                break;
            case SHIP:
                list.add(inbox(msg.getUserId(), "仓库已发货",
                        "售后单 " + no + " 仓库已发货，请确认收货。"));
                break;
            case RECEIVE:
                if (msg.getStatus() != null && msg.getStatus() == TicketStatus.WAIT_REFUND.getCode()) {
                    list.add(inbox(msg.getMerchantId(), "用户已收货，待退款",
                            "售后单 " + no + " 用户已确认收货，请处理退款。"));
                } else {
                    list.add(inbox(msg.getUserId(), "售后已办结",
                            "您的售后单 " + no + " 已办结。"));
                }
                break;
            case REFUND:
                list.add(inbox(msg.getUserId(), "退款已完成",
                        "您的售后单 " + no + " 退款已办结。"));
                break;
            case ROLLBACK:
                list.add(inbox(msg.getUserId(), "售后流程已回退",
                        "您的售后单 " + no + " 已回退至上一节点。"));
                break;
            default:
                break;
        }
        return list;
    }

    private static TicketAction parseAction(String event) {
        if (!StringUtils.hasText(event)) {
            throw new IllegalArgumentException("empty notify event");
        }
        return TicketAction.valueOf(event.trim().toUpperCase());
    }

    private static NotifySendDTO inbox(Long userId, String title, String content) {
        if (userId == null) {
            throw new IllegalArgumentException("notify userId is null, title=" + title);
        }
        NotifySendDTO dto = new NotifySendDTO();
        dto.setUserId(userId);
        dto.setTitle(title);
        dto.setContent(content);
        dto.setChannel("INBOX");
        dto.setBizType("AFTERSALE");
        return dto;
    }
}
