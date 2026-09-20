package com.yean.stock.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.stock.stock.dto.StockFlow;
import com.yean.stock.stock.dto.StockFlowQueryDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StockFlowMapper extends BaseMapper<StockFlow> {

    StockFlow selectByBizNoAndType(@Param("bizNo") String bizNo, @Param("type") String type);

    List<StockFlow> selectFlowList(StockFlowQueryDTO query);
}
