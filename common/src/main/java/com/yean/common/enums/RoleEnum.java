package com.yean.common.enums;

import com.yean.common.exception.BizException;

import java.util.Arrays;
import java.util.Optional;

/**
 * 系统角色。用户可同时拥有多个角色。
 */
public enum RoleEnum {

    USER("普通用户"),
    MERCHANT("商家"),
    ADMIN("管理员"),
    WAREHOUSE("仓库"),
    FINANCE("财务");

    private final String label;

    RoleEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<RoleEnum> of(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    public static RoleEnum require(String code) {
        return of(code).orElseThrow(() -> new BizException(ErrorEnum.ROLE_INVALID, code));
    }
}
