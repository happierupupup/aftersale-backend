package com.yean.trade.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.trade.inventory.dto.InventoryCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryCountMapper extends BaseMapper<InventoryCount> {

    InventoryCount selectBySkuId(@Param("skuId") Long skuId);

    int casDeduct(@Param("skuId") Long skuId,
                  @Param("version") Integer version,
                  @Param("changeNum") Integer changeNum);

    int casRollback(@Param("skuId") Long skuId,
                    @Param("version") Integer version,
                    @Param("changeNum") Integer changeNum);

    int deleteBySkuId(@Param("skuId") Long skuId);
}
