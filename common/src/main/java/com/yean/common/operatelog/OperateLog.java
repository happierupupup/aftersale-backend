package com.yean.common.operatelog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 Controller 方法上，由切面异步写入 portal 操作日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperateLog {

    /** 业务模块，如 user / stock / aftersale */
    String module();

    /** 动作，如 create / update / approve */
    String action();

    /** 备注前缀，可选 */
    String remark() default "";
}
