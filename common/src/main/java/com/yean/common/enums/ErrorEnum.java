package com.yean.common.enums;

/**
 * 统一错误码与返回消息。业务抛 {@code new BizException(ErrorEnum.XXX)}，接口一律用这里的文案。
 */
public enum ErrorEnum {

    PARAM_INVALID(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    TOKEN_INVALID(401, "Token 无效"),
    TOKEN_EXPIRED(401, "Token 已过期"),
    TOKEN_BLACKLISTED(401, "已登出，请重新登陆"),
    FORBIDDEN(403, "无接口访问权限"),
    NOT_FOUND(404, "接口不存在"),
    CONFLICT(409, "操作处理中，请勿重复提交"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
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

    NOTICE_NOT_FOUND(1201, "通知不存在"),

    SKU_NOT_FOUND(2001, "SKU 不存在"),
    SKU_EXISTS(2002, "SKU 已存在"),
    SKU_MERCHANT_MISSING(2003, "SKU 未配置归属商家"),
    STOCK_NOT_ENOUGH(2003, "库存不足"),
    STOCK_CHANGE_CONFLICT(2004, "库存变更冲突，请重试"),

    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_NOT_OWNER(3002, "不能为他人订单申请售后"),
    ORDER_STATUS_NOT_ALLOW(3003, "订单状态不允许售后"),
    ORDER_UNPAID(3004, "订单未支付"),
    AFTERSALE_NUM_INVALID(3005, "售后数量超过购买数量"),
    AFTERSALE_TYPE_INVALID(3006, "非法售后类型"),
    ORDER_EXISTS(3007, "订单号已存在"),
    ORDER_IMPORT_EMPTY(3008, "导入文件为空或无有效数据"),
    ORDER_IMPORT_FORMAT(3009, "导入文件格式错误: %s"),
    AFTERSALE_NOT_FOUND(3101, "售后单不存在"),
    AFTERSALE_STATUS_NOT_ALLOW(3102, "当前状态不允许该操作"),
    AFTERSALE_NO_PERMISSION(3103, "无权操作该售后单"),

    PROCESS_NOT_FOUND(3201, "流程实例不存在"),
    PROCESS_ENDED(3202, "流程已结束，禁止修改"),
    PROCESS_TASK_MISMATCH(3203, "当前节点与操作不匹配"),
    PROCESS_ROLE_DENIED(3204, "当前角色无权处理该节点"),
    PROCESS_ROLLBACK_DENIED(3205, "当前节点不允许回退");

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
