package com.yean.stock.sku.service.impl;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.common.lock.RedisBizLock;
import com.yean.stock.inventory.dto.StockCount;
import com.yean.stock.inventory.mapper.StockCountMapper;
import com.yean.stock.sku.cache.SkuCache;
import com.yean.stock.sku.dto.SkuCreateDTO;
import com.yean.stock.sku.dto.SkuDetail;
import com.yean.stock.sku.dto.SkuQueryDTO;
import com.yean.stock.sku.dto.SkuUpdateDTO;
import com.yean.stock.sku.dto.SkuVO;
import com.yean.stock.sku.dto.StockSku;
import com.yean.stock.sku.mapper.StockSkuMapper;
import com.yean.stock.sku.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    private static final String LOCK_PREFIX = "lock:stock:";

    private final StockSkuMapper stockSkuMapper;
    private final StockCountMapper stockCountMapper;
    private final RedisBizLock redisBizLock;
    private final SkuCache skuCache;
    private final TransactionTemplate transactionTemplate;

    @Override
    public SkuVO getBySkuId(Long skuId) {
        return toVO(requireDetail(skuId));
    }

    @Override
    public List<SkuVO> list(SkuQueryDTO query) {
        SkuQueryDTO cond = query == null ? new SkuQueryDTO() : query;
        return stockSkuMapper.selectDetailList(cond).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Long create(SkuCreateDTO dto, String operator) {
        return redisBizLock.execute(LOCK_PREFIX, dto.getSkuId(), () ->
                transactionTemplate.execute(status -> {
                    if (stockSkuMapper.selectBySkuId(dto.getSkuId()) != null) {
                        throw new BizException(ErrorEnum.SKU_EXISTS);
                    }
                    StockSku sku = new StockSku();
                    sku.setSkuId(dto.getSkuId());
                    sku.setSkuName(dto.getSkuName());
                    sku.setWarnThreshold(dto.getWarnThreshold() == null ? 10 : dto.getWarnThreshold());
                    sku.setUpdateBy(operator);
                    try {
                        stockSkuMapper.insert(sku);
                    } catch (DuplicateKeyException e) {
                        throw new BizException(ErrorEnum.SKU_EXISTS);
                    }
                    StockCount count = new StockCount();
                    count.setSkuId(dto.getSkuId());
                    count.setStockNum(dto.getStockNum());
                    count.setVersion(0);
                    stockCountMapper.insert(count);
                    return sku.getId();
                }));
    }

    @Override
    public void update(Long skuId, SkuUpdateDTO dto, String operator) {
        redisBizLock.execute(LOCK_PREFIX, skuId, () -> {
            StockSku sku = requireSku(skuId);
            if (StringUtils.hasText(dto.getSkuName())) {
                sku.setSkuName(dto.getSkuName());
            }
            if (dto.getWarnThreshold() != null) {
                sku.setWarnThreshold(dto.getWarnThreshold());
            }
            if (StringUtils.hasText(operator)) {
                sku.setUpdateBy(operator);
            }
            stockSkuMapper.updateById(sku);
            skuCache.evict(skuId);
            skuCache.evictDelayed(skuId);
            return null;
        });
    }

    @Override
    public void delete(Long skuId) {
        redisBizLock.execute(LOCK_PREFIX, skuId, () -> {
            StockSku sku = requireSku(skuId);
            stockCountMapper.deleteBySkuId(skuId);
            stockSkuMapper.deleteById(sku.getId());
            skuCache.evict(skuId);
            return null;
        });
    }

    private StockSku requireSku(Long skuId) {
        StockSku sku = stockSkuMapper.selectBySkuId(skuId);
        if (sku == null) {
            throw new BizException(ErrorEnum.SKU_NOT_FOUND);
        }
        return sku;
    }

    private SkuDetail requireDetail(Long skuId) {
        SkuDetail detail = stockSkuMapper.selectDetailBySkuId(skuId);
        if (detail == null) {
            throw new BizException(ErrorEnum.SKU_NOT_FOUND);
        }
        return detail;
    }

    private SkuVO toVO(SkuDetail detail) {
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
}
