package com.yean.trade.ticket.enums;

public enum TicketAction {

    APPLY,
    APPROVE,
    REJECT,
    WITHDRAW,
    /** 仓库发货 */
    SHIP,
    /** 用户确认收货 */
    RECEIVE,
    /** 商家退款办结 */
    REFUND
}
