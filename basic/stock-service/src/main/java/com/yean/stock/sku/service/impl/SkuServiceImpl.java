package com.yean.stock.sku.service.impl;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.stock.config.SkuLock;
import com.yean.stock.sku.cache.SkuCache;
import com.yean.stock.sku.dto.SkuCreateDTO;
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

    private final StockSkuMapper stockSkuMapper;
    private final SkuLock skuLock;
    private final SkuCache skuCache;
    private final TransactionTemplate transactionTemplate;

    @Override
    public SkuVO getBySkuId(Long skuId) {
        return toVO(requireSku(skuId));
    }

    @Override
    public List<SkuVO> list(SkuQueryDTO query) {
        SkuQueryDTO cond = query == null ? new SkuQueryDTO() : query;
        return stockSkuMapper.selectSkuList(cond).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Long create(SkuCreateDTO dto, String operator) {
        return skuLock.execute(dto.getSkuId(), () ->
                transactionTemplate.execute(status -> {
                    if (stockSkuMapper.selectBySkuId(dto.getSkuId()) != null) {
                        throw new BizException(ErrorEnum.SKU_EXISTS);
                    }
                    StockSku sku = new StockSku();
                    sku.setSkuId(dto.getSkuId());
                    sku.setSkuName(dto.getSkuName());
                    sku.setStockNum(dto.getStockNum());
                    sku.setVersion(0);
                    sku.setWarnThreshold(dto.getWarnThreshold() == null ? 10 : dto.getWarnThreshold());
                    sku.setUpdateBy(operator);
                    try {
                        stockSkuMapper.insert(sku);
                    } catch (DuplicateKeyException e) {
                        throw new BizException(ErrorEnum.SKU_EXISTS);
                    }
                    return sku.getId();
                }));
    }

    @Override
    public void update(Long skuId, SkuUpdateDTO dto, String operator) {
        skuLock.execute(skuId, () -> {
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
        skuLock.execute(skuId, () -> {
            StockSku sku = requireSku(skuId);
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

    private SkuVO toVO(StockSku sku) {
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
}
