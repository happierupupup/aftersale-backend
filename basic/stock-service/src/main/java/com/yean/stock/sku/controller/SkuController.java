package com.yean.stock.sku.controller;

import com.yean.common.operator.OperatorResolver;
import com.yean.common.operatelog.OperateLog;
import com.yean.common.result.R;
import com.yean.stock.sku.dto.SkuCreateDTO;
import com.yean.stock.sku.dto.SkuQueryDTO;
import com.yean.stock.sku.dto.SkuUpdateDTO;
import com.yean.stock.sku.dto.SkuVO;
import com.yean.stock.sku.service.SkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "SKU 档案")
@Validated
@RestController
@RequestMapping("/sku")
@RequiredArgsConstructor
public class SkuController {

    private final SkuService skuService;
    private final OperatorResolver operatorResolver;

    @Operation(summary = "SKU 列表")
    @GetMapping("/list")
    public R<List<SkuVO>> list(SkuQueryDTO query) {
        return R.ok(skuService.list(query));
    }

    @Operation(summary = "SKU 详情（读库，不走库存缓存）")
    @GetMapping("/{skuId}")
    public R<SkuVO> get(@PathVariable Long skuId) {
        return R.ok(skuService.getBySkuId(skuId));
    }

    @OperateLog(module = "sku", action = "create")
    @Operation(summary = "新增 SKU 档案")
    @PostMapping
    public R<Long> create(@Valid @RequestBody SkuCreateDTO dto,
                          @Parameter(in = ParameterIn.HEADER, hidden = true)
                          @RequestHeader(value = "X-Username", required = false) String headerUsername,
                          @Parameter(in = ParameterIn.HEADER, hidden = true)
                          @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        return R.ok(skuService.create(dto, operatorResolver.resolveUsername(headerUsername, headerUserId, dto.getUsername())));
    }

    @OperateLog(module = "sku", action = "update")
    @Operation(summary = "修改名称 / 预警阈值，不改可用库存")
    @PutMapping("/{skuId}")
    public R<Void> update(@PathVariable Long skuId, @Valid @RequestBody SkuUpdateDTO dto,
                          @Parameter(in = ParameterIn.HEADER, hidden = true)
                          @RequestHeader(value = "X-Username", required = false) String headerUsername,
                          @Parameter(in = ParameterIn.HEADER, hidden = true)
                          @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        skuService.update(skuId, dto, operatorResolver.resolveUsername(headerUsername, headerUserId, dto.getUsername()));
        return R.ok();
    }

    @OperateLog(module = "sku", action = "delete")
    @Operation(summary = "删除 SKU")
    @DeleteMapping("/{skuId}")
    public R<Void> delete(@PathVariable Long skuId) {
        skuService.delete(skuId);
        return R.ok();
    }
}
