package com.yean.trade.ticket.strategy;

import com.yean.trade.ticket.strategy.impl.MerchantApproveStrategy;
import com.yean.trade.ticket.strategy.impl.WarehouseShipStrategy;
import com.yean.trade.ticket.strategy.impl.UserReceiveStrategy;
import com.yean.trade.ticket.strategy.impl.MerchantRefundStrategy;
import com.yean.trade.ticket.strategy.impl.RejectNodeStrategy;
import com.yean.trade.ticket.strategy.impl.WithdrawNodeStrategy;
import com.yean.trade.ticket.strategy.impl.RollbackNodeStrategy;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.trade.ticket.enums.TicketAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按售后操作取节点策略：factory.getStrategy(action)。
 */
@Component
@RequiredArgsConstructor
public class TicketNodeStrategyFactory {

    private final MerchantApproveStrategy merchantApproveStrategy;
    private final WarehouseShipStrategy warehouseShipStrategy;
    private final UserReceiveStrategy userReceiveStrategy;
    private final MerchantRefundStrategy merchantRefundStrategy;
    private final RejectNodeStrategy rejectNodeStrategy;
    private final WithdrawNodeStrategy withdrawNodeStrategy;
    private final RollbackNodeStrategy rollbackNodeStrategy;

    public TicketNodeStrategy getStrategy(TicketAction action) {
        if (action == null) {
            throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
        }
        switch (action) {
            case APPROVE:
                return merchantApproveStrategy;
            case SHIP:
                return warehouseShipStrategy;
            case RECEIVE:
                return userReceiveStrategy;
            case REFUND:
                return merchantRefundStrategy;
            case REJECT:
                return rejectNodeStrategy;
            case WITHDRAW:
                return withdrawNodeStrategy;
            case ROLLBACK:
                return rollbackNodeStrategy;
            default:
                throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
        }
    }
}
