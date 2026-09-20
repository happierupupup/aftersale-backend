package com.yean.stock.stock.service.impl;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.stock.config.SkuLock;
import com.yean.stock.sku.cache.SkuCache;
import com.yean.stock.sku.dto.SkuVO;
import com.yean.stock.sku.dto.StockSku;
import com.yean.stock.sku.mapper.StockSkuMapper;
import com.yean.stock.stock.dto.StockChangeDTO;
import com.yean.stock.stock.dto.StockChangeVO;
import com.yean.stock.stock.dto.StockFlow;
import com.yean.stock.stock.dto.StockFlowQueryDTO;
import com.yean.stock.stock.dto.StockFlowVO;
import com.yean.stock.stock.enums.StockFlowType;
import com.yean.stock.stock.mapper.StockFlowMapper;
import com.yean.stock.stock.service.StockService;
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
public class StockServiceImpl implements StockService {

    private static final int MAX_CAS_RETRY = 3;
    private static final String FLOW_SUCCESS = "SUCCESS";

    private final StockSkuMapper stockSkuMapper;
    private final StockFlowMapper stockFlowMapper;
    private final SkuLock skuLock;
    private final SkuCache skuCache;
    private final TransactionTemplate transactionTemplate;

    @Override
    public StockChangeVO deduct(StockChangeDTO dto, String operator) {
        return change(dto, StockFlowType.DEDUCT, operator);
    }

    @Override
    public StockChangeVO rollback(StockChangeDTO dto, String operator) {
        return change(dto, StockFlowType.ROLLBACK, operator);
    }

    @Override
    public SkuVO getBySkuId(Long skuId) {
        SkuVO cached = skuCache.get(skuId);
        if (cached != null) {
            return cached;
        }
        SkuVO vo = toSkuVO(requireSku(skuId));
        skuCache.put(vo);
        return vo;
    }

    @Override
    public List<StockFlowVO> listFlows(StockFlowQueryDTO query) {
        StockFlowQueryDTO cond = query == null ? new StockFlowQueryDTO() : query;
        return stockFlowMapper.selectFlowList(cond).stream().map(this::toFlowVO).collect(Collectors.toList());
    }

    private StockChangeVO change(StockChangeDTO dto, StockFlowType type, String operator) {
        StockFlow exist = stockFlowMapper.selectByBizNoAndType(dto.getBizNo(), type.name());
        if (exist != null) {
            return toChangeVO(exist, true);
        }
        BizException lastConflict = null;
        for (int i = 0; i < MAX_CAS_RETRY; i++) {
            try {
                StockChangeVO vo = skuLock.execute(dto.getSkuId(), () -> doChangeInLock(dto, type, operator));
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

    private StockChangeVO doChangeInLock(StockChangeDTO dto, StockFlowType type, String operator) {
        StockFlow exist = stockFlowMapper.selectByBizNoAndType(dto.getBizNo(), type.name());
        if (exist != null) {
            return toChangeVO(exist, true);
        }
        StockSku sku = requireSku(dto.getSkuId());
        if (type == StockFlowType.DEDUCT && sku.getStockNum() < dto.getChangeNum()) {
            throw new BizException(ErrorEnum.STOCK_NOT_ENOUGH);
        }
        StockChangeVO vo = transactionTemplate.execute(status -> {
            int rows = type == StockFlowType.DEDUCT
                    ? stockSkuMapper.casDeduct(dto.getSkuId(), sku.getVersion(), dto.getChangeNum())
                    : stockSkuMapper.casRollback(dto.getSkuId(), sku.getVersion(), dto.getChangeNum());
            if (rows == 0) {
                throw new BizException(ErrorEnum.STOCK_CHANGE_CONFLICT);
            }
            StockFlow flow = new StockFlow();
            flow.setFlowNo("SF" + UUID.randomUUID().toString().replace("-", ""));
            flow.setSkuId(dto.getSkuId());
            flow.setChangeNum(dto.getChangeNum());
            flow.setType(type.name());
            flow.setBizNo(dto.getBizNo());
            flow.setStatus(FLOW_SUCCESS);
            flow.setOperator(operator);
            flow.setRemark(dto.getRemark());
            try {
                stockFlowMapper.insert(flow);
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                return null;
            }
            StockSku after = stockSkuMapper.selectBySkuId(dto.getSkuId());
            return toChangeVO(flow, after, false);
        });
        if (vo == null) {
            StockFlow duplicated = stockFlowMapper.selectByBizNoAndType(dto.getBizNo(), type.name());
            if (duplicated != null) {
                return toChangeVO(duplicated, true);
            }
            throw new BizException(ErrorEnum.STOCK_CHANGE_CONFLICT);
        }
        return vo;
    }

    private void afterChange(StockChangeVO vo) {
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

    private StockSku requireSku(Long skuId) {
        StockSku sku = stockSkuMapper.selectBySkuId(skuId);
        if (sku == null) {
            throw new BizException(ErrorEnum.SKU_NOT_FOUND);
        }
        return sku;
    }

    private SkuVO toSkuVO(StockSku sku) {
        SkuVO vo = new SkuVO();
        vo.setId(sku.getId());
        vo.setSkuId(sku.getSkuId());
        vo.setSkuName(sku.getSkuName());
        vo.setStockNum(sku.getStockNum());
        vo.setVersion(sku.getVersion());
        vo.setWarnThreshold(sku.getWarnThreshold());
        vo.setUpdateBy(sku.getUpdateBy());
        vo.setUpdateTime(sku.getUpdateTime());
        return vo;
    }

    private StockChangeVO toChangeVO(StockFlow flow, boolean idempotent) {
        StockSku sku = stockSkuMapper.selectBySkuId(flow.getSkuId());
        return toChangeVO(flow, sku, idempotent);
    }

    private StockChangeVO toChangeVO(StockFlow flow, StockSku sku, boolean idempotent) {
        StockChangeVO vo = new StockChangeVO();
        vo.setFlowNo(flow.getFlowNo());
        vo.setSkuId(flow.getSkuId());
        vo.setChangeNum(flow.getChangeNum());
        vo.setType(flow.getType());
        vo.setBizNo(flow.getBizNo());
        vo.setOperator(flow.getOperator());
        vo.setStockNum(sku == null ? null : sku.getStockNum());
        vo.setWarnThreshold(sku == null ? null : sku.getWarnThreshold());
        vo.setIdempotent(idempotent);
        return vo;
    }

    private StockFlowVO toFlowVO(StockFlow flow) {
        StockFlowVO vo = new StockFlowVO();
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
