package com.yean.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sku")
public class SkuCacheProperties {

    /** Cache Aside 基础 TTL（秒），实际再加 0–300 随机防雪崩 */
    private int cacheTtlSeconds = 300;
}
