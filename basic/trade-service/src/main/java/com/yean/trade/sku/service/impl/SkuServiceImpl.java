package com.yean.trade.sku.service.impl;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.common.lock.RedisBizLock;
import com.yean.common.result.PageResult;
import com.yean.trade.inventory.dto.InventoryCount;
import com.yean.trade.inventory.mapper.InventoryCountMapper;
import com.yean.trade.sku.cache.SkuCache;
import com.yean.trade.sku.dto.SkuCreateDTO;
import com.yean.trade.sku.dto.SkuDetail;
import com.yean.trade.sku.dto.SkuQueryDTO;
import com.yean.trade.sku.dto.SkuUpdateDTO;
import com.yean.trade.sku.dto.SkuVO;
import com.yean.trade.sku.dto.Sku;
import com.yean.trade.sku.mapper.SkuMapper;
import com.yean.trade.sku.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    private static final String LOCK_PREFIX = "lock:stock:";

    private final SkuMapper skuMapper;
    private final InventoryCountMapper inventoryCountMapper;
    private final RedisBizLock redisBizLock;
    private final SkuCache skuCache;
    private final TransactionTemplate transactionTemplate;

    @Override
    public SkuVO getBySkuId(Long skuId) {
        return toVO(requireDetail(skuId));
    }

    @Override
    public PageResult<SkuVO> list(SkuQueryDTO query) {
        SkuQueryDTO cond = query == null ? new SkuQueryDTO() : query;
        IPage<SkuDetail> result = skuMapper.selectDetailList(
                new Page<>(cond.pageOrDefault(), cond.sizeOrDefault()), cond);
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
    }

    @Override
    public Long create(SkuCreateDTO dto, String operator) {
        return redisBizLock.execute(LOCK_PREFIX, dto.getSkuId(), () -> {
            // Same as inventory: open tx under stock DS, not primary core.
            DynamicDataSourceContextHolder.push("stock");
            try {
                return transactionTemplate.execute(status -> {
                    if (skuMapper.selectBySkuId(dto.getSkuId()) != null) {
                        throw new BizException(ErrorEnum.SKU_EXISTS);
                    }
                    Sku sku = new Sku();
                    sku.setSkuId(dto.getSkuId());
                    sku.setSkuName(dto.getSkuName());
                    sku.setWarnThreshold(dto.getWarnThreshold() == null ? 10 : dto.getWarnThreshold());
                    sku.setUpdateBy(operator);
                    try {
                        skuMapper.insert(sku);
                    } catch (DuplicateKeyException e) {
                        throw new BizException(ErrorEnum.SKU_EXISTS);
                    }
                    InventoryCount count = new InventoryCount();
                    count.setSkuId(dto.getSkuId());
                    count.setStockNum(dto.getStockNum());
                    count.setVersion(0);
                    inventoryCountMapper.insert(count);
                    return sku.getId();
                });
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
        });
    }

    @Override
    public void update(Long skuId, SkuUpdateDTO dto, String operator) {
        redisBizLock.execute(LOCK_PREFIX, skuId, () -> {
            Sku sku = requireSku(skuId);
            if (StringUtils.hasText(dto.getSkuName())) {
                sku.setSkuName(dto.getSkuName());
            }
            if (dto.getWarnThreshold() != null) {
                sku.setWarnThreshold(dto.getWarnThreshold());
            }
            if (StringUtils.hasText(operator)) {
                sku.setUpdateBy(operator);
            }
            skuMapper.updateById(sku);
            skuCache.evict(skuId);
            skuCache.evictDelayed(skuId);
            return null;
        });
    }

    @Override
    public void delete(Long skuId) {
        redisBizLock.execute(LOCK_PREFIX, skuId, () -> {
            Sku sku = requireSku(skuId);
            inventoryCountMapper.deleteBySkuId(skuId);
            skuMapper.deleteById(sku.getId());
            skuCache.evict(skuId);
            return null;
        });
    }

    private Sku requireSku(Long skuId) {
        Sku sku = skuMapper.selectBySkuId(skuId);
        if (sku == null) {
            throw new BizException(ErrorEnum.SKU_NOT_FOUND);
        }
        return sku;
    }

    private SkuDetail requireDetail(Long skuId) {
        SkuDetail detail = skuMapper.selectDetailBySkuId(skuId);
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
