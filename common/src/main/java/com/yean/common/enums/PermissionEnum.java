package com.yean.common.enums;

import com.yean.common.exception.BizException;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 权限码。角色与权限的映射集中在这里，业务侧只问 hasPermission。
 */
public enum PermissionEnum {

    USER_MANAGE("user:manage", "用户管理", RoleEnum.ADMIN),
    DEPT_MANAGE("dept:manage", "部门管理", RoleEnum.ADMIN),
    ROLE_GRANT("role:grant", "角色授权", RoleEnum.ADMIN),
    USER_QUERY("user:query", "查询用户", RoleEnum.ADMIN, RoleEnum.MERCHANT, RoleEnum.WAREHOUSE, RoleEnum.USER),
    AFTERSALE_APPLY("aftersale:apply", "提交售后", RoleEnum.USER, RoleEnum.ADMIN),
    AFTERSALE_MERCHANT_APPROVE("aftersale:merchant:approve", "商家审核", RoleEnum.MERCHANT, RoleEnum.ADMIN),
    AFTERSALE_WAREHOUSE("aftersale:warehouse", "仓库发货", RoleEnum.WAREHOUSE, RoleEnum.ADMIN),
    AFTERSALE_REFUND("aftersale:refund", "商家退款", RoleEnum.MERCHANT, RoleEnum.ADMIN),
    STOCK_MANAGE("stock:manage", "库存管理", RoleEnum.WAREHOUSE, RoleEnum.ADMIN);

    private final String code;
    private final String label;
    private final Set<RoleEnum> roles;

    PermissionEnum(String code, String label, RoleEnum... roles) {
        this.code = code;
        this.label = label;
        this.roles = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(roles)));
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public Set<RoleEnum> getRoles() {
        return roles;
    }

    public boolean grantedTo(RoleEnum role) {
        return role == RoleEnum.ADMIN || roles.contains(role);
    }

    public static Optional<PermissionEnum> of(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code.trim()) || item.name().equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    public static PermissionEnum require(String code) {
        return of(code).orElseThrow(() -> new BizException(ErrorEnum.PERMISSION_INVALID, code));
    }
}
