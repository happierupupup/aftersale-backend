package com.yean.trade.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.trade.inventory.dto.InventoryFlow;
import com.yean.trade.inventory.dto.InventoryFlowQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryFlowMapper extends BaseMapper<InventoryFlow> {

    InventoryFlow selectByBizNoAndType(@Param("bizNo") String bizNo, @Param("type") String type);

    IPage<InventoryFlow> selectFlowList(IPage<InventoryFlow> page, @Param("q") InventoryFlowQueryDTO query);
}
