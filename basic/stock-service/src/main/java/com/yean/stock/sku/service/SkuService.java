package com.yean.stock.sku.service;

import com.yean.stock.sku.dto.SkuCreateDTO;
import com.yean.stock.sku.dto.SkuQueryDTO;
import com.yean.stock.sku.dto.SkuUpdateDTO;
import com.yean.stock.sku.dto.SkuVO;

import java.util.List;

public interface SkuService {

    SkuVO getBySkuId(Long skuId);

    List<SkuVO> list(SkuQueryDTO query);

    Long create(SkuCreateDTO dto, String operator);

    void update(Long skuId, SkuUpdateDTO dto, String operator);

    void delete(Long skuId);
}
