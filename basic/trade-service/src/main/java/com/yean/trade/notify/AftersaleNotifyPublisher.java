package com.yean.trade.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yean.trade.message.service.LocalMessageService;
import com.yean.trade.notify.dto.AftersaleNotifyMessage;
import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.enums.TicketAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 售后通知入 outbox（local_message）。须在业务本地事务内调用，由 Sender 扫表发 MQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AftersaleNotifyPublisher {

    public static final String TOPIC = "aftersale_notify";

    private final LocalMessageService localMessageService;
    private final ObjectMapper objectMapper;

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
            localMessageService.insert(msg.getBizId(), TOPIC, objectMapper.writeValueAsString(msg));
            log.info("aftersale notify enqueued, bizId={}", msg.getBizId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize aftersale notify failed, bizId=" + msg.getBizId(), e);
        }
    }
}
