package com.yean.aftersale.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class OperatorResolver {

    private final AftersaleProperties aftersaleProperties;

    public Operator resolve(String headerUserId, String headerUsername, String headerRole,
                            Long bodyUserId, String bodyUsername, String bodyRole) {
        Long userId = parseLong(headerUserId);
        String username = trimToNull(headerUsername);
        String role = trimToNull(headerRole);
        if (aftersaleProperties.isAllowBodyOperator()) {
            if (userId == null) {
                userId = bodyUserId;
            }
            if (username == null) {
                username = trimToNull(bodyUsername);
            }
            if (role == null) {
                role = trimToNull(bodyRole);
            }
        }
        return new Operator(userId, username, role);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
