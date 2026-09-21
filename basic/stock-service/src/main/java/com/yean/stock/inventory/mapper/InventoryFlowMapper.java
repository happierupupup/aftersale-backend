package com.yean.stock.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.stock.inventory.dto.InventoryFlow;
import com.yean.stock.inventory.dto.InventoryFlowQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryFlowMapper extends BaseMapper<InventoryFlow> {

    InventoryFlow selectByBizNoAndType(@Param("bizNo") String bizNo, @Param("type") String type);

    List<InventoryFlow> selectFlowList(InventoryFlowQueryDTO query);
}
