package com.yean.trade.ticket.strategy.impl;

import com.yean.trade.ticket.strategy.TicketNodeContext;
import com.yean.trade.ticket.strategy.TicketNodeStrategy;

import com.yean.trade.ticket.enums.TicketStatus;
import org.springframework.stereotype.Component;

@Component
public class RejectNodeStrategy implements TicketNodeStrategy {

    @Override
    public int resolveNextStatus(TicketNodeContext ctx) {
        return TicketStatus.REJECTED.getCode();
    }
}
