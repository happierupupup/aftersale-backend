package com.yean.trade.ticket.enums;

/**
 * 售后主链路（精简）：
 * 申请 → 待审核 →（通过）待仓库发货 → 待用户收货 → 待退款 → 已办结
 * 仅退款：审核通过后跳过发货/收货，直接待退款。
 * 驳回仅允许待审核；撤销允许待审核或待仓库发货（发货前）。
 */
public enum TicketStatus {

    PENDING(0, "待审核"),
    WAIT_SHIP(1, "待仓库发货"),
    WAIT_RECEIVE(2, "待用户收货"),
    WAIT_REFUND(3, "待退款"),
    DONE(4, "已办结"),
    REJECTED(5, "已驳回"),
    CANCELED(6, "已撤回");

    private final int code;
    private final String label;

    TicketStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
