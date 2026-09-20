package com.yean.stock.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 正式：只信网关头。本地 8083：allow-body-username=true 时可用 body.username。
 */
@Component
@RequiredArgsConstructor
public class OperatorResolver {

    private final StockProperties stockProperties;

    public String resolve(String headerUsername, String headerUserId, String bodyUsername) {
        if (StringUtils.hasText(headerUsername)) {
            return headerUsername.trim();
        }
        if (StringUtils.hasText(headerUserId)) {
            return headerUserId.trim();
        }
        if (stockProperties.isAllowBodyUsername() && StringUtils.hasText(bodyUsername)) {
            return bodyUsername.trim();
        }
        return null;
    }
}
