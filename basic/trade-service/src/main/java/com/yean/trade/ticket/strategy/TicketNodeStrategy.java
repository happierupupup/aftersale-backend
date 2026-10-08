package com.yean.trade.ticket.strategy;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.trade.ticket.enums.TicketStatus;

/**
 * 流程节点完成后的业务处理：算下一售后状态 + 副作用（如库存）。
 */
public interface TicketNodeStrategy {

    /** 流程 RPC 成功后对应的 TicketStatus 码 */
    int resolveNextStatus(TicketNodeContext ctx);

    /**
     * 库存等副作用入口（T2：终态只读）。
     * 子类覆盖 {@link #doAfterProcess}，不要直接覆盖本方法。
     */
    default void afterProcess(TicketNodeContext ctx) {
        if (ctx != null && ctx.getRow() != null && TicketStatus.isTerminal(ctx.getRow().getStatus())) {
            throw new BizException(ErrorEnum.PROCESS_ENDED.getCode(), "售后单已终态，禁止修改");
        }
        doAfterProcess(ctx);
    }

    /** 实际副作用；默认无操作 */
    default void doAfterProcess(TicketNodeContext ctx) {
    }
}
