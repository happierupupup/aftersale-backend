package com.yean.trade.order.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_info")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long merchantId;
    private Long skuId;
    private String skuName;
    private Integer num;
    private BigDecimal amount;
    private Integer status;
    private Integer payStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
