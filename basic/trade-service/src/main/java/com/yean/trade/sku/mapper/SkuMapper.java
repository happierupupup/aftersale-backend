package com.yean.trade.sku.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.trade.sku.dto.SkuDetail;
import com.yean.trade.sku.dto.SkuQueryDTO;
import com.yean.trade.sku.dto.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    Sku selectBySkuId(@Param("skuId") Long skuId);

    SkuDetail selectDetailBySkuId(@Param("skuId") Long skuId);

    IPage<SkuDetail> selectDetailList(IPage<SkuDetail> page, @Param("q") SkuQueryDTO query);
}
