package com.yean.aftersale.aftersale.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "aftersale_order", autoResultMap = true)
public class AftersaleOrder {

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
    private Integer num;
    private BigDecimal amount;
    private String reason;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private LogisticsInfo logisticsInfo;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private RefundDetail refundDetail;
    private String processInstanceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
