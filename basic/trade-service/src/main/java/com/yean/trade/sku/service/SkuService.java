package com.yean.trade.sku.service;

import com.yean.common.result.PageResult;
import com.yean.trade.sku.dto.SkuCreateDTO;
import com.yean.trade.sku.dto.SkuQueryDTO;
import com.yean.trade.sku.dto.SkuUpdateDTO;
import com.yean.trade.sku.dto.SkuVO;

public interface SkuService {

    SkuVO getBySkuId(Long skuId);

    PageResult<SkuVO> list(SkuQueryDTO query);

    Long create(SkuCreateDTO dto, String operator);

    void update(Long skuId, SkuUpdateDTO dto, String operator);

    void delete(Long skuId);

    /** 按 SKU 解析归属商家；档案不存在或未配置商家则抛业务异常 */
    Long requireMerchantId(Long skuId);
}
