package com.yean.trade.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yean.trade.message.service.LocalMessageService;
import com.yean.trade.notify.config.NotifyProperties;
import com.yean.trade.notify.dto.AftersaleNotifyMessage;
import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.enums.TicketAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 售后通知：默认写 outbox（local_message）；{@code yean.notify.delivery=sync} 时请求内直调 portal（§9.4 对照）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AftersaleNotifyPublisher {

    public static final String TOPIC = "aftersale_notify";

    private final LocalMessageService localMessageService;
    private final AftersaleNotifySender aftersaleNotifySender;
    private final NotifyProperties notifyProperties;
    private final ObjectMapper objectMapper;

    public void publish(Ticket ticket, TicketAction action) {
        if (ticket == null || action == null || ticket.getAftersaleNo() == null) {
            return;
        }
        dispatch(ticket, action.name(), ticket.getAftersaleNo() + ":" + action.name());
    }

    /** 超时关单通知申请人（与人工驳回文案区分） */
    public void publishTimeoutClose(Ticket ticket) {
        if (ticket == null || ticket.getAftersaleNo() == null) {
            return;
        }
        dispatch(ticket, "TIMEOUT_CLOSE", ticket.getAftersaleNo() + ":TIMEOUT_CLOSE");
    }

    private void dispatch(Ticket ticket, String event, String bizId) {
        AftersaleNotifyMessage msg = toMessage(ticket, event, bizId);
        if (notifyProperties.isSyncDelivery()) {
            aftersaleNotifySender.sendToPortal(msg);
            log.info("aftersale notify sync sent, bizId={}", bizId);
            return;
        }
        try {
            localMessageService.insert(msg.getBizId(), TOPIC, objectMapper.writeValueAsString(msg));
            log.info("aftersale notify enqueued, bizId={}", bizId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize aftersale notify failed, bizId=" + bizId, e);
        }
    }

    private static AftersaleNotifyMessage toMessage(Ticket ticket, String event, String bizId) {
        AftersaleNotifyMessage msg = new AftersaleNotifyMessage();
        msg.setBizId(bizId);
        msg.setAftersaleNo(ticket.getAftersaleNo());
        msg.setEvent(event);
        msg.setUserId(ticket.getUserId());
        msg.setMerchantId(ticket.getMerchantId());
        msg.setStatus(ticket.getStatus());
        msg.setType(ticket.getType());
        msg.setSkuName(ticket.getSkuName());
        return msg;
    }
}
