package com.yean.aftersale.ticket.controller;

import com.yean.aftersale.ticket.dto.TicketActionDTO;
import com.yean.aftersale.ticket.dto.TicketApplyDTO;
import com.yean.aftersale.ticket.dto.TicketQueryDTO;
import com.yean.aftersale.ticket.dto.TicketVO;
import com.yean.aftersale.ticket.service.TicketService;
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
@RequestMapping("/aftersale")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final OperatorResolver operatorResolver;

    @Operation(summary = "申请售后。USER；同一订单同一 SKU 仅一张进行中的单，驳回/撤回后可重新申请")
    @PostMapping("/apply")
    public R<TicketVO> apply(@Valid @RequestBody TicketApplyDTO dto,
                             @Parameter(in = ParameterIn.HEADER, hidden = true)
                             @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                             @Parameter(in = ParameterIn.HEADER, hidden = true)
                             @RequestHeader(value = "X-Username", required = false) String headerUsername,
                             @Parameter(in = ParameterIn.HEADER, hidden = true)
                             @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(ticketService.apply(dto, operator));
    }

    @Operation(summary = "售后列表，按角色过滤：USER 看自己的，MERCHANT 看本店的")
    @GetMapping("/page")
    public R<List<TicketVO>> page(TicketQueryDTO query,
                                  @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                  @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                  @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        return R.ok(ticketService.page(query, operator));
    }

    @Operation(summary = "售后详情 + 操作日志")
    @GetMapping("/{aftersaleNo}")
    public R<TicketVO> detail(@PathVariable String aftersaleNo,
                              @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                              @RequestHeader(value = "X-Username", required = false) String headerUsername,
                              @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        return R.ok(ticketService.detail(aftersaleNo, operator));
    }

    @Operation(summary = "审核通过。MERCHANT / WAREHOUSE / FINANCE，仅待审核")
    @PostMapping("/approve")
    public R<TicketVO> approve(@Valid @RequestBody TicketActionDTO dto,
                               @Parameter(in = ParameterIn.HEADER, hidden = true)
                               @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                               @Parameter(in = ParameterIn.HEADER, hidden = true)
                               @RequestHeader(value = "X-Username", required = false) String headerUsername,
                               @Parameter(in = ParameterIn.HEADER, hidden = true)
                               @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(ticketService.approve(dto, operator));
    }

    @Operation(summary = "驳回。MERCHANT / WAREHOUSE / FINANCE，仅待审核；驳回后可重新申请")
    @PostMapping("/reject")
    public R<TicketVO> reject(@Valid @RequestBody TicketActionDTO dto,
                              @Parameter(in = ParameterIn.HEADER, hidden = true)
                              @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                              @Parameter(in = ParameterIn.HEADER, hidden = true)
                              @RequestHeader(value = "X-Username", required = false) String headerUsername,
                              @Parameter(in = ParameterIn.HEADER, hidden = true)
                              @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(ticketService.reject(dto, operator));
    }

    @Operation(summary = "撤回。USER，仅待审核；状态置为已撤回，可重新申请")
    @PostMapping("/withdraw")
    public R<TicketVO> withdraw(@Valid @RequestBody TicketActionDTO dto,
                                @Parameter(in = ParameterIn.HEADER, hidden = true)
                                @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                @Parameter(in = ParameterIn.HEADER, hidden = true)
                                @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                @Parameter(in = ParameterIn.HEADER, hidden = true)
                                @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(ticketService.withdraw(dto, operator));
    }
}
