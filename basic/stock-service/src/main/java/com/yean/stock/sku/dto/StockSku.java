package com.yean.stock.sku.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_sku")
public class StockSku {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private String skuName;
    private Integer warnThreshold;
    /** 最后一次维护档案的操作人 */
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
