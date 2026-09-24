package com.yean.stock.sku.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.stock.sku.dto.SkuDetail;
import com.yean.stock.sku.dto.SkuQueryDTO;
import com.yean.stock.sku.dto.StockSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockSkuMapper extends BaseMapper<StockSku> {

    StockSku selectBySkuId(@Param("skuId") Long skuId);

    SkuDetail selectDetailBySkuId(@Param("skuId") Long skuId);

    IPage<SkuDetail> selectDetailList(IPage<SkuDetail> page, @Param("q") SkuQueryDTO query);
}
