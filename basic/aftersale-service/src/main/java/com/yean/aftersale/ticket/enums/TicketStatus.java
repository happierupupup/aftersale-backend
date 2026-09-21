package com.yean.aftersale.ticket.enums;

public enum TicketStatus {

    PENDING(0, "待审核"),
    APPROVED(1, "审核通过"),
    WAIT_SHIP(2, "待寄回"),
    WAIT_INSPECT(3, "待验货"),
    WAIT_REFUND(4, "待退款"),
    DONE(5, "已办结"),
    REJECTED(6, "已驳回"),
    CANCELED(7, "已撤回");

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
