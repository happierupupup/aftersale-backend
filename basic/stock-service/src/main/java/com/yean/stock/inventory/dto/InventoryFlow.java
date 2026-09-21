package com.yean.stock.inventory.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_flow")
public class InventoryFlow {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowNo;
    private Long skuId;
    private Integer changeNum;
    private String type;
    private String bizNo;
    private String status;
    /** 扣减/回退操作人，网关 X-Username 或本地调试 username */
    private String operator;
    private String remark;
    private LocalDateTime createTime;
}
