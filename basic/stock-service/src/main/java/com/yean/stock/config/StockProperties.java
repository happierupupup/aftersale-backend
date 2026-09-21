package com.yean.stock.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "stock")
public class StockProperties {

    /** Cache Aside 基础 TTL（秒），实际再加 0–300 随机防雪崩 */
    private int cacheTtlSeconds = 300;
}
