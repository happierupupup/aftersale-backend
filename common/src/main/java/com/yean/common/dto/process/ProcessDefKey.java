package com.yean.common.dto.process;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;

/**
 * BPMN process definition key，与售后类型对应。
 */
public enum ProcessDefKey {

    RETURN("return"),
    EXCHANGE("exchange"),
    REFUND("refund");

    private final String key;

    ProcessDefKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /** 售后 type：1退货 2换货 3仅退款 */
    public static ProcessDefKey fromTicketType(Integer type) {
        if (type == null) {
            throw new BizException(ErrorEnum.AFTERSALE_TYPE_INVALID);
        }
        switch (type) {
            case 1:
                return RETURN;
            case 2:
                return EXCHANGE;
            case 3:
                return REFUND;
            default:
                throw new BizException(ErrorEnum.AFTERSALE_TYPE_INVALID);
        }
    }
}
