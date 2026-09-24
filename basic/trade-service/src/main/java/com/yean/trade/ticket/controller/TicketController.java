package com.yean.trade.ticket.controller;

import com.yean.trade.ticket.dto.TicketActionDTO;
import com.yean.trade.ticket.dto.TicketApplyDTO;
import com.yean.trade.ticket.dto.TicketQueryDTO;
import com.yean.trade.ticket.dto.TicketVO;
import com.yean.trade.ticket.service.TicketService;
import com.yean.common.operator.Operator;
import com.yean.common.operator.OperatorResolver;
import com.yean.common.operatelog.OperateLog;
import com.yean.common.result.PageResult;
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

@Tag(name = "售后单")
@Validated
@RestController
@RequestMapping("/aftersale")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final OperatorResolver operatorResolver;

    @OperateLog(module = "aftersale", action = "apply")
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

    @Operation(summary = "售后分页列表。page/size 默认 1/10，size 上限 50")
    @GetMapping("/page")
    public R<PageResult<TicketVO>> page(TicketQueryDTO query,
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

    @OperateLog(module = "aftersale", action = "approve")
    @Operation(summary = "商家审核通过。待审核 → 待仓库发货（仅退款 → 待退款）")
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

    @OperateLog(module = "aftersale", action = "reject")
    @Operation(summary = "驳回。仅待审核；驳回后可重新申请")
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

    @OperateLog(module = "aftersale", action = "withdraw")
    @Operation(summary = "撤回。USER，仅待审核")
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

    @OperateLog(module = "aftersale", action = "ship")
    @Operation(summary = "仓库发货。待仓库发货 → 待用户收货；须填 logisticsInfo")
    @PostMapping("/ship")
    public R<TicketVO> ship(@Valid @RequestBody TicketActionDTO dto,
                            @Parameter(in = ParameterIn.HEADER, hidden = true)
                            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                            @Parameter(in = ParameterIn.HEADER, hidden = true)
                            @RequestHeader(value = "X-Username", required = false) String headerUsername,
                            @Parameter(in = ParameterIn.HEADER, hidden = true)
                            @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(ticketService.ship(dto, operator));
    }

    @OperateLog(module = "aftersale", action = "receive")
    @Operation(summary = "用户确认收货。待用户收货 → 待退款")
    @PostMapping("/receive")
    public R<TicketVO> receive(@Valid @RequestBody TicketActionDTO dto,
                               @Parameter(in = ParameterIn.HEADER, hidden = true)
                               @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                               @Parameter(in = ParameterIn.HEADER, hidden = true)
                               @RequestHeader(value = "X-Username", required = false) String headerUsername,
                               @Parameter(in = ParameterIn.HEADER, hidden = true)
                               @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(ticketService.receive(dto, operator));
    }

    @OperateLog(module = "aftersale", action = "refund")
    @Operation(summary = "商家退款办结。待退款 → 已办结；可填 refundDetail")
    @PostMapping("/refund")
    public R<TicketVO> refund(@Valid @RequestBody TicketActionDTO dto,
                              @Parameter(in = ParameterIn.HEADER, hidden = true)
                              @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                              @Parameter(in = ParameterIn.HEADER, hidden = true)
                              @RequestHeader(value = "X-Username", required = false) String headerUsername,
                              @Parameter(in = ParameterIn.HEADER, hidden = true)
                              @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(ticketService.refund(dto, operator));
    }
}
