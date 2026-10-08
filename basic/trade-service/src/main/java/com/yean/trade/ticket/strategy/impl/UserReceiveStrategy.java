package com.yean.trade.ticket.strategy.impl;

import com.yean.trade.ticket.strategy.TicketNodeContext;
import com.yean.trade.ticket.strategy.TicketNodeStrategy;

import com.yean.common.dto.Operator;
import com.yean.trade.inventory.dto.InventoryChangeDTO;
import com.yean.trade.inventory.service.InventoryService;
import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.enums.TicketStatus;
import com.yean.trade.ticket.enums.TicketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class UserReceiveStrategy implements TicketNodeStrategy {

    private final InventoryService inventoryService;

    @Override
    public int resolveNextStatus(TicketNodeContext ctx) {
        // 换货无退款节点，收货即办结
        if (TicketType.require(ctx.getRow().getType()) == TicketType.EXCHANGE) {
            return TicketStatus.DONE.getCode();
        }
        return TicketStatus.WAIT_REFUND.getCode();
    }

    @Override
    public void doAfterProcess(TicketNodeContext ctx) {
        Ticket row = ctx.getRow();
        TicketType type = TicketType.require(row.getType());
        if (type == TicketType.REFUND_ONLY) {
            return;
        }
        InventoryChangeDTO dto = new InventoryChangeDTO();
        dto.setSkuId(row.getSkuId());
        dto.setChangeNum(row.getNum());
        dto.setBizNo(row.getAftersaleNo());
        dto.setRemark(type == TicketType.RETURN ? "RETURN_RECEIVE" : "EXCHANGE_RECEIVE");
        inventoryService.rollback(dto, opName(ctx.getOperator()));
    }

    private static String opName(Operator operator) {
        if (StringUtils.hasText(operator.getUsername())) {
            return operator.getUsername();
        }
        return operator.getUserId() == null ? "system" : String.valueOf(operator.getUserId());
    }
}
