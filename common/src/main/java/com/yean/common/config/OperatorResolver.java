package com.yean.common.config;

import com.yean.common.dto.Operator;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class OperatorResolver {

    private final OperatorProperties properties;

    /**
     * 完整操作人：优先 Header，allow-body 时可用 body 补齐。
     */
    public Operator resolve(String headerUserId, String headerUsername, String headerRole,
                            Long bodyUserId, String bodyUsername, String bodyRole) {
        Long userId = parseLong(headerUserId);
        String username = trimToNull(headerUsername);
        String role = trimToNull(headerRole);
        if (properties.isAllowBody()) {
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

    /**
     * 库存等只记操作人名称：X-Username → X-User-Id → body.username（本地）。
     */
    public String resolveUsername(String headerUsername, String headerUserId, String bodyUsername) {
        if (StringUtils.hasText(headerUsername)) {
            return headerUsername.trim();
        }
        if (StringUtils.hasText(headerUserId)) {
            return headerUserId.trim();
        }
        if (properties.isAllowBody() && StringUtils.hasText(bodyUsername)) {
            return bodyUsername.trim();
        }
        return null;
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
