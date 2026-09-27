package com.yean.trade.ticket.strategy;

/**
 * 流程节点完成后的业务处理：算下一售后状态 + 副作用（如库存）。
 */
public interface TicketNodeStrategy {

    /** 流程 RPC 成功后对应的 TicketStatus 码 */
    int resolveNextStatus(TicketNodeContext ctx);

    /** 库存等副作用；默认无操作 */
    default void afterProcess(TicketNodeContext ctx) {
    }
}
