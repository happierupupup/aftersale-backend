package com.yean.trade.inventory.service;

import com.yean.common.result.PageResult;
import com.yean.trade.inventory.dto.InventoryChangeDTO;
import com.yean.trade.inventory.dto.InventoryChangeVO;
import com.yean.trade.inventory.dto.InventoryFlowQueryDTO;
import com.yean.trade.inventory.dto.InventoryFlowVO;
import com.yean.trade.sku.dto.SkuVO;

public interface InventoryService {

    InventoryChangeVO deduct(InventoryChangeDTO dto, String operator);

    InventoryChangeVO rollback(InventoryChangeDTO dto, String operator);

    SkuVO getBySkuId(Long skuId);

    PageResult<InventoryFlowVO> listFlows(InventoryFlowQueryDTO query);
}
