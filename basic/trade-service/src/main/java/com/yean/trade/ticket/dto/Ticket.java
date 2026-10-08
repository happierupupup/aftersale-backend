package com.yean.trade.ticket.dto;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "aftersale_ticket", autoResultMap = true)
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String aftersaleNo;
    private String orderNo;
    private Long userId;
    private Long merchantId;
    private Integer type;
    private Integer status;
    private Long skuId;
    private String skuName;
    /** 换货目标 SKU；仅 type=EXCHANGE 有值 */
    private Long exchangeSkuId;
    private Integer num;
    private BigDecimal amount;
    private String reason;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private LogisticsInfo logisticsInfo;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private RefundDetail refundDetail;
    /**
     * 进行中=1；草稿/驳回/撤回=NULL。
     * ALWAYS：保证草稿 insert 能写入 NULL（否则默认 NOT_NULL 策略会跳过，落到列 DEFAULT 1）。
     */
    @TableField(insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.ALWAYS)
    private Integer activeFlag;
    private String processInstanceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
