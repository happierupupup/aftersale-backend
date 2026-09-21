package com.yean.stock.inventory.service.impl;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.common.lock.RedisBizLock;
import com.yean.stock.sku.cache.SkuCache;
import com.yean.stock.sku.dto.SkuDetail;
import com.yean.stock.sku.dto.SkuVO;
import com.yean.stock.sku.mapper.StockSkuMapper;
import com.yean.stock.inventory.dto.InventoryChangeDTO;
import com.yean.stock.inventory.dto.InventoryChangeVO;
import com.yean.stock.inventory.dto.InventoryFlow;
import com.yean.stock.inventory.dto.InventoryFlowQueryDTO;
import com.yean.stock.inventory.dto.InventoryFlowVO;
import com.yean.stock.inventory.dto.StockCount;
import com.yean.stock.inventory.enums.InventoryFlowType;
import com.yean.stock.inventory.mapper.InventoryFlowMapper;
import com.yean.stock.inventory.mapper.StockCountMapper;
import com.yean.stock.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final int MAX_CAS_RETRY = 3;
    private static final String FLOW_SUCCESS = "SUCCESS";
    private static final String LOCK_PREFIX = "lock:stock:";

    private final StockSkuMapper stockSkuMapper;
    private final StockCountMapper stockCountMapper;
    private final InventoryFlowMapper inventoryFlowMapper;
    private final RedisBizLock redisBizLock;
    private final SkuCache skuCache;
    private final TransactionTemplate transactionTemplate;

    @Override
    public InventoryChangeVO deduct(InventoryChangeDTO dto, String operator) {
        return change(dto, InventoryFlowType.DEDUCT, operator);
    }

    @Override
    public InventoryChangeVO rollback(InventoryChangeDTO dto, String operator) {
        return change(dto, InventoryFlowType.ROLLBACK, operator);
    }

    @Override
    public SkuVO getBySkuId(Long skuId) {
        SkuVO cached = skuCache.get(skuId);
        if (cached != null) {
            return cached;
        }
        SkuVO vo = toSkuVO(requireDetail(skuId));
        skuCache.put(vo);
        return vo;
    }

    @Override
    public List<InventoryFlowVO> listFlows(InventoryFlowQueryDTO query) {
        InventoryFlowQueryDTO cond = query == null ? new InventoryFlowQueryDTO() : query;
        return inventoryFlowMapper.selectFlowList(cond).stream().map(this::toFlowVO).collect(Collectors.toList());
    }

    private InventoryChangeVO change(InventoryChangeDTO dto, InventoryFlowType type, String operator) {
        InventoryFlow exist = inventoryFlowMapper.selectByBizNoAndType(dto.getBizNo(), type.name());
        if (exist != null) {
            return toChangeVO(exist, true);
        }
        BizException lastConflict = null;
        for (int i = 0; i < MAX_CAS_RETRY; i++) {
            try {
                InventoryChangeVO vo = redisBizLock.execute(LOCK_PREFIX, dto.getSkuId(),
                        () -> doChangeInLock(dto, type, operator));
                afterChange(vo);
                return vo;
            } catch (BizException e) {
                if (e.getCode() == ErrorEnum.STOCK_CHANGE_CONFLICT.getCode()) {
                    lastConflict = e;
                    continue;
                }
                throw e;
            }
        }
        throw lastConflict != null ? lastConflict : new BizException(ErrorEnum.STOCK_CHANGE_CONFLICT);
    }

    private InventoryChangeVO doChangeInLock(InventoryChangeDTO dto, InventoryFlowType type, String operator) {
        InventoryFlow exist = inventoryFlowMapper.selectByBizNoAndType(dto.getBizNo(), type.name());
        if (exist != null) {
            return toChangeVO(exist, true);
        }
        SkuDetail detail = requireDetail(dto.getSkuId());
        StockCount count = stockCountMapper.selectBySkuId(dto.getSkuId());
        if (count == null) {
            throw new BizException(ErrorEnum.SKU_NOT_FOUND);
        }
        if (type == InventoryFlowType.DEDUCT && count.getStockNum() < dto.getChangeNum()) {
            throw new BizException(ErrorEnum.STOCK_NOT_ENOUGH);
        }
        InventoryChangeVO vo = transactionTemplate.execute(status -> {
            int rows = type == InventoryFlowType.DEDUCT
                    ? stockCountMapper.casDeduct(dto.getSkuId(), count.getVersion(), dto.getChangeNum())
                    : stockCountMapper.casRollback(dto.getSkuId(), count.getVersion(), dto.getChangeNum());
            if (rows == 0) {
                throw new BizException(ErrorEnum.STOCK_CHANGE_CONFLICT);
            }
            InventoryFlow flow = new InventoryFlow();
            flow.setFlowNo("SF" + UUID.randomUUID().toString().replace("-", ""));
            flow.setSkuId(dto.getSkuId());
            flow.setChangeNum(dto.getChangeNum());
            flow.setType(type.name());
            flow.setBizNo(dto.getBizNo());
            flow.setStatus(FLOW_SUCCESS);
            flow.setOperator(operator);
            flow.setRemark(dto.getRemark());
            try {
                inventoryFlowMapper.insert(flow);
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                return null;
            }
            StockCount after = stockCountMapper.selectBySkuId(dto.getSkuId());
            return toChangeVO(flow, after, detail.getWarnThreshold(), false);
        });
        if (vo == null) {
            InventoryFlow duplicated = inventoryFlowMapper.selectByBizNoAndType(dto.getBizNo(), type.name());
            if (duplicated != null) {
                return toChangeVO(duplicated, true);
            }
            throw new BizException(ErrorEnum.STOCK_CHANGE_CONFLICT);
        }
        return vo;
    }

    private void afterChange(InventoryChangeVO vo) {
        if (vo == null || Boolean.TRUE.equals(vo.getIdempotent())) {
            return;
        }
        skuCache.evict(vo.getSkuId());
        skuCache.evictDelayed(vo.getSkuId());
        if (vo.getStockNum() != null && vo.getWarnThreshold() != null
                && vo.getStockNum() < vo.getWarnThreshold()) {
            log.warn("stock below threshold, skuId={}, stock={}, threshold={}, operator={}",
                    vo.getSkuId(), vo.getStockNum(), vo.getWarnThreshold(), vo.getOperator());
        }
    }

    private SkuDetail requireDetail(Long skuId) {
        SkuDetail detail = stockSkuMapper.selectDetailBySkuId(skuId);
        if (detail == null) {
            throw new BizException(ErrorEnum.SKU_NOT_FOUND);
        }
        return detail;
    }

    private SkuVO toSkuVO(SkuDetail detail) {
        SkuVO vo = new SkuVO();
        vo.setId(detail.getId());
        vo.setSkuId(detail.getSkuId());
        vo.setSkuName(detail.getSkuName());
        vo.setStockNum(detail.getStockNum());
        vo.setVersion(detail.getVersion());
        vo.setWarnThreshold(detail.getWarnThreshold());
        vo.setUpdateBy(detail.getUpdateBy());
        vo.setUpdateTime(detail.getUpdateTime());
        return vo;
    }

    private InventoryChangeVO toChangeVO(InventoryFlow flow, boolean idempotent) {
        SkuDetail detail = stockSkuMapper.selectDetailBySkuId(flow.getSkuId());
        StockCount count = detail == null ? null : stockCountMapper.selectBySkuId(flow.getSkuId());
        Integer warn = detail == null ? null : detail.getWarnThreshold();
        return toChangeVO(flow, count, warn, idempotent);
    }

    private InventoryChangeVO toChangeVO(InventoryFlow flow, StockCount count, Integer warnThreshold, boolean idempotent) {
        InventoryChangeVO vo = new InventoryChangeVO();
        vo.setFlowNo(flow.getFlowNo());
        vo.setSkuId(flow.getSkuId());
        vo.setChangeNum(flow.getChangeNum());
        vo.setType(flow.getType());
        vo.setBizNo(flow.getBizNo());
        vo.setOperator(flow.getOperator());
        vo.setStockNum(count == null ? null : count.getStockNum());
        vo.setWarnThreshold(warnThreshold);
        vo.setIdempotent(idempotent);
        return vo;
    }

    private InventoryFlowVO toFlowVO(InventoryFlow flow) {
        InventoryFlowVO vo = new InventoryFlowVO();
        vo.setFlowNo(flow.getFlowNo());
        vo.setSkuId(flow.getSkuId());
        vo.setChangeNum(flow.getChangeNum());
        vo.setType(flow.getType());
        vo.setBizNo(flow.getBizNo());
        vo.setStatus(flow.getStatus());
        vo.setOperator(flow.getOperator());
        vo.setRemark(flow.getRemark());
        vo.setCreateTime(flow.getCreateTime());
        return vo;
    }
}
