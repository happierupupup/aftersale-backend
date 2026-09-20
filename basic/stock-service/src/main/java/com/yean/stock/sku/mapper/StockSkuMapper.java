package com.yean.stock.sku.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.stock.sku.dto.SkuQueryDTO;
import com.yean.stock.sku.dto.StockSku;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StockSkuMapper extends BaseMapper<StockSku> {

    StockSku selectBySkuId(@Param("skuId") Long skuId);

    List<StockSku> selectSkuList(SkuQueryDTO query);

    int casDeduct(@Param("skuId") Long skuId,
                  @Param("version") Integer version,
                  @Param("changeNum") Integer changeNum);

    int casRollback(@Param("skuId") Long skuId,
                    @Param("version") Integer version,
                    @Param("changeNum") Integer changeNum);
}
