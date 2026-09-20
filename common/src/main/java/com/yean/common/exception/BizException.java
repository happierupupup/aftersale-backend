package com.yean.common.exception;

import com.yean.common.enums.ErrorEnum;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorEnum error) {
        this(error.getCode(), error.getMsg());
    }

    public BizException(ErrorEnum error, Object... args) {
        this(error.getCode(), error.format(args));
    }

    public BizException(String msg) {
        this(ErrorEnum.SYSTEM_ERROR.getCode(), msg);
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
