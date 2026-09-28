package com.yean.trade.notify;

import com.yean.trade.notify.dto.AftersaleNotifyMessage;
import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.enums.TicketAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 业务提交成功后直发 MQ（无 outbox）。发送失败只记日志，不回滚主链路。
 * 下一步再用 local_message 保证「改单 + 待投递」同事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AftersaleNotifyPublisher {

    public static final String TOPIC = "aftersale_notify";

    private final RocketMQTemplate rocketMQTemplate;

    public void publish(Ticket ticket, TicketAction action) {
        if (ticket == null || action == null || ticket.getAftersaleNo() == null) {
            return;
        }
        AftersaleNotifyMessage msg = new AftersaleNotifyMessage();
        msg.setBizId(ticket.getAftersaleNo() + ":" + action.name());
        msg.setAftersaleNo(ticket.getAftersaleNo());
        msg.setEvent(action.name());
        msg.setUserId(ticket.getUserId());
        msg.setMerchantId(ticket.getMerchantId());
        msg.setStatus(ticket.getStatus());
        msg.setType(ticket.getType());
        msg.setSkuName(ticket.getSkuName());
        try {
            rocketMQTemplate.convertAndSend(TOPIC, msg);
            log.info("aftersale notify published, bizId={}", msg.getBizId());
        } catch (Exception e) {
            log.error("aftersale notify publish failed, bizId={}, err={}", msg.getBizId(), e.getMessage());
        }
    }
}
