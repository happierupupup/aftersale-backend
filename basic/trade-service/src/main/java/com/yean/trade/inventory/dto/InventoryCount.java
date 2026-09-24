package com.yean.trade.inventory.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_count")
public class InventoryCount {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private Integer stockNum;
    /** 乐观锁版本号，扣减/回退用 CAS SQL 更新 */
    private Integer version;
    private LocalDateTime updateTime;
}
