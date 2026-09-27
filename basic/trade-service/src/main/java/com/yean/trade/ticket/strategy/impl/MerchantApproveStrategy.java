package com.yean.trade.ticket.strategy.impl;

import com.yean.trade.ticket.strategy.TicketNodeContext;
import com.yean.trade.ticket.strategy.TicketNodeStrategy;

import com.yean.common.dto.process.ProcessDefKey;
import com.yean.trade.ticket.enums.TicketStatus;
import com.yean.trade.ticket.enums.TicketType;
import org.springframework.stereotype.Component;

@Component
public class MerchantApproveStrategy implements TicketNodeStrategy {

    @Override
    public int resolveNextStatus(TicketNodeContext ctx) {
        // 仅退款：待退款；退货/换货：待仓库发货
        if (TicketType.require(ctx.getRow().getType()) == TicketType.REFUND_ONLY
                || ProcessDefKey.REFUND.getKey().equals(ctx.getProcess().getProcessDefKey())) {
            return TicketStatus.WAIT_REFUND.getCode();
        }
        return TicketStatus.WAIT_SHIP.getCode();
    }
}
