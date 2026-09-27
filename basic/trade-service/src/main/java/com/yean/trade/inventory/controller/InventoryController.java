package com.yean.trade.inventory.controller;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.enums.PermissionEnum;
import com.yean.common.enums.RoleEnum;
import com.yean.common.exception.BizException;
import com.yean.common.operator.Operator;
import com.yean.common.operator.OperatorResolver;
import com.yean.common.operatelog.OperateLog;
import com.yean.common.result.PageResult;
import com.yean.common.result.R;
import com.yean.trade.sku.dto.SkuVO;
import com.yean.trade.inventory.dto.InventoryChangeDTO;
import com.yean.trade.inventory.dto.InventoryChangeVO;
import com.yean.trade.inventory.dto.InventoryFlowQueryDTO;
import com.yean.trade.inventory.dto.InventoryFlowVO;
import com.yean.trade.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Tag(name = "库存变动")
@Validated
@RestController
@RequestMapping("/stock")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final OperatorResolver operatorResolver;

    @OperateLog(module = "stock", action = "deduct")
    @Operation(summary = "扣减库存（WAREHOUSE/ADMIN；幂等键：bizNo + DEDUCT）")
    @PostMapping("/deduct")
    public R<InventoryChangeVO> deduct(@Valid @RequestBody InventoryChangeDTO dto,
                                       @Parameter(in = ParameterIn.HEADER, hidden = true)
                                       @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                       @Parameter(in = ParameterIn.HEADER, hidden = true)
                                       @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                       @Parameter(in = ParameterIn.HEADER, hidden = true)
                                       @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = requireStockManage(headerUserId, headerUsername, headerRole, dto);
        return R.ok(inventoryService.deduct(dto, resolveOpName(operator, dto)));
    }

    @OperateLog(module = "stock", action = "rollback")
    @Operation(summary = "回退库存（WAREHOUSE/ADMIN；幂等键：bizNo + ROLLBACK）")
    @PostMapping("/rollback")
    public R<InventoryChangeVO> rollback(@Valid @RequestBody InventoryChangeDTO dto,
                                         @Parameter(in = ParameterIn.HEADER, hidden = true)
                                         @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                         @Parameter(in = ParameterIn.HEADER, hidden = true)
                                         @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                         @Parameter(in = ParameterIn.HEADER, hidden = true)
                                         @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = requireStockManage(headerUserId, headerUsername, headerRole, dto);
        return R.ok(inventoryService.rollback(dto, resolveOpName(operator, dto)));
    }

    @Operation(summary = "查当前可用库存（Cache Aside）")
    @GetMapping("/{skuId}")
    public R<SkuVO> get(@PathVariable Long skuId) {
        return R.ok(inventoryService.getBySkuId(skuId));
    }

    @Operation(summary = "库存流水分页。page/size 默认 1/10，size 上限 50")
    @GetMapping("/flow/list")
    public R<PageResult<InventoryFlowVO>> flows(InventoryFlowQueryDTO query) {
        return R.ok(inventoryService.listFlows(query));
    }

    private Operator requireStockManage(String headerUserId, String headerUsername, String headerRole,
                                        InventoryChangeDTO dto) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        RoleEnum role = RoleEnum.of(operator.getRole()).orElse(null);
        if (role == null || !PermissionEnum.STOCK_MANAGE.grantedTo(role)) {
            throw new BizException(ErrorEnum.NO_GRANT_PERMISSION);
        }
        return operator;
    }

    private static String resolveOpName(Operator operator, InventoryChangeDTO dto) {
        if (StringUtils.hasText(operator.getUsername())) {
            return operator.getUsername();
        }
        if (StringUtils.hasText(dto.getUsername())) {
            return dto.getUsername();
        }
        return operator.getUserId() == null ? null : String.valueOf(operator.getUserId());
    }
}
