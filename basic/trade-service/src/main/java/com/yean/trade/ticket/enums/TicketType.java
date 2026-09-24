package com.yean.trade.ticket.enums;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;

public enum TicketType {

    RETURN(1, "退货"),
    EXCHANGE(2, "换货"),
    REFUND_ONLY(3, "仅退款");

    private final int code;
    private final String label;

    TicketType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static TicketType require(Integer code) {
        if (code == null) {
            throw new BizException(ErrorEnum.AFTERSALE_TYPE_INVALID);
        }
        for (TicketType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new BizException(ErrorEnum.AFTERSALE_TYPE_INVALID);
    }
}
