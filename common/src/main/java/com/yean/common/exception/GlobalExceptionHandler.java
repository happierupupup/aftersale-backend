package com.yean.common.exception;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValid(Exception e) {
        String msg = ErrorEnum.PARAM_INVALID.getMsg();
        if (e instanceof MethodArgumentNotValidException valid) {
            if (valid.getBindingResult().getFieldError() != null) {
                msg = valid.getBindingResult().getFieldError().getDefaultMessage();
            }
        } else if (e instanceof BindException bind && bind.getFieldError() != null) {
            msg = bind.getFieldError().getDefaultMessage();
        }
        return R.fail(ErrorEnum.PARAM_INVALID.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("unhandled error", e);
        return R.fail(ErrorEnum.SYSTEM_ERROR);
    }
}
