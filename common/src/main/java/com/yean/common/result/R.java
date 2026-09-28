package com.yean.common.result;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import lombok.Data;

@Data
public class R<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(0);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(ErrorEnum error) {
        return fail(error.getCode(), error.getMsg());
    }

    public static <T> R<T> fail(ErrorEnum error, Object... args) {
        return fail(error.getCode(), error.format(args));
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    /** Feign / HTTP 调用结果解包：失败或空响应抛 BizException。 */
    public static <T> T unwrap(R<T> r) {
        if (r == null) {
            throw new BizException("远程调用无响应");
        }
        if (r.getCode() != 0) {
            throw new BizException(r.getCode(), r.getMsg());
        }
        return r.getData();
    }
}
