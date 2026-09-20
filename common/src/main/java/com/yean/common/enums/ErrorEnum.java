package com.yean.common.enums;

/**
 * 统一错误码与返回消息。业务抛 {@code new BizException(ErrorEnum.XXX)}，接口一律用这里的文案。
 */
public enum ErrorEnum {

    PARAM_INVALID(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    TOKEN_INVALID(401, "Token 无效"),
    TOKEN_EXPIRED(401, "Token 已过期"),
    TOKEN_BLACKLISTED(401, "已登出"),
    NOT_FOUND(404, "接口不存在"),
    CONFLICT(409, "操作处理中，请勿重复提交"),
    SYSTEM_ERROR(500, "系统异常"),
    GATEWAY_DOWN(502, "下游服务不可用"),

    USER_OR_PASSWORD_ERROR(1001, "用户名或密码错误"),
    USER_DISABLED(1002, "账号已禁用"),
    USER_NOT_FOUND(1003, "用户不存在"),
    USERNAME_EXISTS(1004, "用户名已存在"),
    ROLE_REQUIRED(1005, "至少分配一个角色"),
    NO_GRANT_PERMISSION(1006, "无授权权限"),
    PERMISSION_INVALID(1007, "非法权限码: %s"),
    ROLE_INVALID(1008, "非法角色: %s"),

    DEPT_NOT_FOUND(1103, "部门不存在"),
    DEPT_PARENT_SELF(1104, "父部门不能是自己"),
    DEPT_HAS_CHILDREN(1105, "存在子部门，无法删除"),
    DEPT_HAS_USERS(1106, "部门下仍有用户，无法删除"),
    DEPT_PARENT_NOT_FOUND(1107, "父部门不存在"),

    NOTICE_NOT_FOUND(1201, "通知不存在");

    private final int code;
    private final String msg;

    ErrorEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public String format(Object... args) {
        if (args == null || args.length == 0 || !msg.contains("%")) {
            return msg;
        }
        return String.format(msg, args);
    }
}
