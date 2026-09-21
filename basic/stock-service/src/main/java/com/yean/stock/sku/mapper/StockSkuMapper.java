package com.yean.stock.sku.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.stock.sku.dto.SkuDetail;
import com.yean.stock.sku.dto.SkuQueryDTO;
import com.yean.stock.sku.dto.StockSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockSkuMapper extends BaseMapper<StockSku> {

    StockSku selectBySkuId(@Param("skuId") Long skuId);

    SkuDetail selectDetailBySkuId(@Param("skuId") Long skuId);

    List<SkuDetail> selectDetailList(SkuQueryDTO query);
}
