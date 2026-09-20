package com.yean.aftersale.aftersale.controller;

import com.yean.aftersale.aftersale.dto.AftersaleActionDTO;
import com.yean.aftersale.aftersale.dto.AftersaleApplyDTO;
import com.yean.aftersale.aftersale.dto.AftersaleQueryDTO;
import com.yean.aftersale.aftersale.dto.AftersaleVO;
import com.yean.aftersale.aftersale.service.AftersaleService;
import com.yean.aftersale.config.Operator;
import com.yean.aftersale.config.OperatorResolver;
import com.yean.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "售后单")
@Validated
@RestController
@RequestMapping("/api/aftersale")
@RequiredArgsConstructor
public class AftersaleController {

    private final AftersaleService aftersaleService;
    private final OperatorResolver operatorResolver;

    @Operation(summary = "申请售后。USER；同一订单同一 SKU 只生成一张单")
    @PostMapping("/apply")
    public R<AftersaleVO> apply(@Valid @RequestBody AftersaleApplyDTO dto,
                                @Parameter(in = ParameterIn.HEADER, hidden = true)
                                @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                @Parameter(in = ParameterIn.HEADER, hidden = true)
                                @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                @Parameter(in = ParameterIn.HEADER, hidden = true)
                                @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(aftersaleService.apply(dto, operator));
    }

    @Operation(summary = "售后列表，按角色过滤：USER 看自己的，MERCHANT 看本店的")
    @GetMapping("/page")
    public R<List<AftersaleVO>> page(AftersaleQueryDTO query,
                                     @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                     @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                     @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        return R.ok(aftersaleService.page(query, operator));
    }

    @Operation(summary = "售后详情 + 操作日志")
    @GetMapping("/{aftersaleNo}")
    public R<AftersaleVO> detail(@PathVariable String aftersaleNo,
                                 @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                 @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                 @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        return R.ok(aftersaleService.detail(aftersaleNo, operator));
    }

    @Operation(summary = "审核通过。MERCHANT / WAREHOUSE / FINANCE，仅待审核")
    @PostMapping("/approve")
    public R<AftersaleVO> approve(@Valid @RequestBody AftersaleActionDTO dto,
                                  @Parameter(in = ParameterIn.HEADER, hidden = true)
                                  @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                  @Parameter(in = ParameterIn.HEADER, hidden = true)
                                  @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                  @Parameter(in = ParameterIn.HEADER, hidden = true)
                                  @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(aftersaleService.approve(dto, operator));
    }

    @Operation(summary = "驳回。MERCHANT / WAREHOUSE / FINANCE，仅待审核")
    @PostMapping("/reject")
    public R<AftersaleVO> reject(@Valid @RequestBody AftersaleActionDTO dto,
                                 @Parameter(in = ParameterIn.HEADER, hidden = true)
                                 @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                 @Parameter(in = ParameterIn.HEADER, hidden = true)
                                 @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                 @Parameter(in = ParameterIn.HEADER, hidden = true)
                                 @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(aftersaleService.reject(dto, operator));
    }

    @Operation(summary = "撤回。USER，仅待审核")
    @PostMapping("/withdraw")
    public R<AftersaleVO> withdraw(@Valid @RequestBody AftersaleActionDTO dto,
                                   @Parameter(in = ParameterIn.HEADER, hidden = true)
                                   @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                   @Parameter(in = ParameterIn.HEADER, hidden = true)
                                   @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                   @Parameter(in = ParameterIn.HEADER, hidden = true)
                                   @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(aftersaleService.withdraw(dto, operator));
    }
}
