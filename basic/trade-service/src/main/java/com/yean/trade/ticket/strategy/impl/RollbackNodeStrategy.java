package com.yean.trade.ticket.strategy.impl;

import com.yean.trade.ticket.strategy.TicketNodeContext;
import com.yean.trade.ticket.strategy.TicketNodeStrategy;

import com.yean.common.dto.process.ProcessTaskKeys;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.trade.ticket.enums.TicketStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RollbackNodeStrategy implements TicketNodeStrategy {

    @Override
    public int resolveNextStatus(TicketNodeContext ctx) {
        String processDefKey = ctx.getProcess().getProcessDefKey();
        String currentTask = ctx.getProcess().getCurrentTask() == null
                ? null : ctx.getProcess().getCurrentTask().getTaskDefinitionKey();
        Integer status = statusForCurrentTask(processDefKey, currentTask);
        if (status == null) {
            throw new BizException(ErrorEnum.PROCESS_ROLLBACK_DENIED);
        }
        return status;
    }

    /** 停在某 userTask = 上一节点刚完成对应的售后状态；首节点=待审核 */
    static Integer statusForCurrentTask(String processDefKey, String currentTaskKey) {
        List<String> order = ProcessTaskKeys.flowOrder(processDefKey);
        int idx = order.indexOf(currentTaskKey);
        if (idx < 0) {
            return null;
        }
        if (idx == 0) {
            return TicketStatus.PENDING.getCode();
        }
        String prevCompleted = order.get(idx - 1);
        if (ProcessTaskKeys.MERCHANT_APPROVE.equals(prevCompleted)) {
            return ProcessTaskKeys.flowOrder(processDefKey).contains(ProcessTaskKeys.WAREHOUSE_SHIP)
                    ? TicketStatus.WAIT_SHIP.getCode() : TicketStatus.WAIT_REFUND.getCode();
        }
        if (ProcessTaskKeys.WAREHOUSE_SHIP.equals(prevCompleted)) {
            return TicketStatus.WAIT_RECEIVE.getCode();
        }
        if (ProcessTaskKeys.USER_RECEIVE.equals(prevCompleted)) {
            return TicketStatus.WAIT_REFUND.getCode();
        }
        return null;
    }
}
