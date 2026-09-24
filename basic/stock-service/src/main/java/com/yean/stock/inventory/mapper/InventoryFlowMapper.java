package com.yean.stock.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.stock.inventory.dto.InventoryFlow;
import com.yean.stock.inventory.dto.InventoryFlowQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryFlowMapper extends BaseMapper<InventoryFlow> {

    InventoryFlow selectByBizNoAndType(@Param("bizNo") String bizNo, @Param("type") String type);

    IPage<InventoryFlow> selectFlowList(IPage<InventoryFlow> page, @Param("q") InventoryFlowQueryDTO query);
}
