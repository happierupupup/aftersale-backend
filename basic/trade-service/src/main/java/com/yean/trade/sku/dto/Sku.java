package com.yean.trade.sku.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_sku")
public class Sku {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private String skuName;
    /** 归属商家用户 ID，售后申请/通知以此为准 */
    private Long merchantId;
    private Integer warnThreshold;
    /** 最后一次维护档案的操作人 */
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
