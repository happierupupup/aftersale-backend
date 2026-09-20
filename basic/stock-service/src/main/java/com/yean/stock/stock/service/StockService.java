package com.yean.stock.stock.service;

import com.yean.stock.sku.dto.SkuVO;
import com.yean.stock.stock.dto.StockChangeDTO;
import com.yean.stock.stock.dto.StockChangeVO;
import com.yean.stock.stock.dto.StockFlowQueryDTO;
import com.yean.stock.stock.dto.StockFlowVO;

import java.util.List;

public interface StockService {

    StockChangeVO deduct(StockChangeDTO dto, String operator);

    StockChangeVO rollback(StockChangeDTO dto, String operator);

    SkuVO getBySkuId(Long skuId);

    List<StockFlowVO> listFlows(StockFlowQueryDTO query);
}
