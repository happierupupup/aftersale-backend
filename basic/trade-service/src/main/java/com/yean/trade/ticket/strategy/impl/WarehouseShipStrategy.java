package com.yean.trade.ticket.strategy.impl;

import com.yean.trade.ticket.strategy.TicketNodeContext;
import com.yean.trade.ticket.strategy.TicketNodeStrategy;

import com.yean.common.dto.Operator;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
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
public class WarehouseShipStrategy implements TicketNodeStrategy {

    private final InventoryService inventoryService;

    @Override
    public int resolveNextStatus(TicketNodeContext ctx) {
        return TicketStatus.WAIT_RECEIVE.getCode();
    }

    @Override
    public void afterProcess(TicketNodeContext ctx) {
        Ticket row = ctx.getRow();
        if (TicketType.require(row.getType()) != TicketType.EXCHANGE) {
            return;
        }
        if (row.getExchangeSkuId() == null) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "换货单缺少 exchangeSkuId");
        }
        InventoryChangeDTO dto = new InventoryChangeDTO();
        dto.setSkuId(row.getExchangeSkuId());
        dto.setChangeNum(row.getNum());
        dto.setBizNo(row.getAftersaleNo());
        dto.setRemark("EXCHANGE_SHIP");
        inventoryService.deduct(dto, opName(ctx.getOperator()));
    }

    private static String opName(Operator operator) {
        if (StringUtils.hasText(operator.getUsername())) {
            return operator.getUsername();
        }
        return operator.getUserId() == null ? "system" : String.valueOf(operator.getUserId());
    }
}
