package com.yean.stock.sku.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 档案 + 计数联查结果（非表实体）。
 */
@Data
public class SkuDetail {

    private Long id;
    private Long skuId;
    private String skuName;
    private Integer stockNum;
    private Integer version;
    private Integer warnThreshold;
    private String updateBy;
    private LocalDateTime updateTime;
}
