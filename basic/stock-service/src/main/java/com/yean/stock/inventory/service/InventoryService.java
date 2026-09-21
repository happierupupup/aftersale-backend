package com.yean.stock.inventory.service;

import com.yean.stock.sku.dto.SkuVO;
import com.yean.stock.inventory.dto.InventoryChangeDTO;
import com.yean.stock.inventory.dto.InventoryChangeVO;
import com.yean.stock.inventory.dto.InventoryFlowQueryDTO;
import com.yean.stock.inventory.dto.InventoryFlowVO;

import java.util.List;

public interface InventoryService {

    InventoryChangeVO deduct(InventoryChangeDTO dto, String operator);

    InventoryChangeVO rollback(InventoryChangeDTO dto, String operator);

    SkuVO getBySkuId(Long skuId);

    List<InventoryFlowVO> listFlows(InventoryFlowQueryDTO query);
}
