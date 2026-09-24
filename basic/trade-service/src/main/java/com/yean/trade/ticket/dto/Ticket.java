package com.yean.trade.ticket.dto;

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
    private Integer num;
    private BigDecimal amount;
    private String reason;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private LogisticsInfo logisticsInfo;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private RefundDetail refundDetail;
    /** 进行中=1；驳回/撤回置 NULL，释放 (order_no, sku_id) 申请位，允许重新申请 */
    private Integer activeFlag;
    private String processInstanceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
