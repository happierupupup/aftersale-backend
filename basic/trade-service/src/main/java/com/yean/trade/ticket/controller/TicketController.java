package com.yean.trade.ticket.controller;

import com.yean.trade.ticket.dto.TicketActionDTO;
import com.yean.trade.ticket.dto.TicketApplyDTO;
import com.yean.trade.ticket.dto.TicketDraftDTO;
import com.yean.trade.ticket.dto.TicketQueryDTO;
import com.yean.trade.ticket.dto.TicketVO;
import com.yean.trade.ticket.service.TicketService;
import com.yean.common.dto.Operator;
import com.yean.common.config.OperatorResolver;
import com.yean.common.rpc.OperateLog;
import com.yean.common.result.PageResult;
import com.yean.common.result.R;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @OperateLog(module = "aftersale", action = "apply")
    @Operation(summary = "提交售后（正式申请）。若有同订单+SKU 草稿先删除再新增正式单；同一进行中单仅一张")
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

    @Operation(summary = "保存草稿。USER；同用户+订单+SKU upsert；不启流程、商家不可见；VO.draft=true")
    @PostMapping("/draft")
    public R<TicketVO> saveDraft(@Valid @RequestBody TicketDraftDTO dto,
                                 @Parameter(in = ParameterIn.HEADER, hidden = true)
                                 @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                 @Parameter(in = ParameterIn.HEADER, hidden = true)
                                 @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                 @Parameter(in = ParameterIn.HEADER, hidden = true)
                                 @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(ticketService.saveDraft(dto, operator));
    }

    @Operation(summary = "当前用户全部草稿列表（进申请页可扫 draft=true 回填）")
    @GetMapping("/draft/mine")
    public R<List<TicketVO>> listMyDrafts(@RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                          @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                          @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        return R.ok(ticketService.listMyDrafts(operator));
    }

    @Operation(summary = "按订单+SKU 查当前用户草稿；无则 data=null")
    @GetMapping("/draft")
    public R<TicketVO> getMyDraft(@RequestParam String orderNo,
                                  @RequestParam Long skuId,
                                  @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                  @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                  @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        return R.ok(ticketService.getMyDraft(orderNo, skuId, operator));
    }

    @Operation(summary = "删除草稿")
    @DeleteMapping("/draft/{aftersaleNo}")
    public R<Void> deleteDraft(@PathVariable String aftersaleNo,
                               @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                               @RequestHeader(value = "X-Username", required = false) String headerUsername,
                               @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        ticketService.deleteDraft(aftersaleNo, operator);
        return R.ok();
    }

    @Operation(summary = "售后分页列表。默认排除草稿；page/size 默认 1/10，size 上限 50；"
            + "USER 仅本人、MERCHANT 仅本商家、WAREHOUSE/ADMIN 不限归属")
    @GetMapping("/page")
    public R<PageResult<TicketVO>> page(TicketQueryDTO query,
                                        @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                        @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                        @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        return R.ok(ticketService.page(query, operator));
    }

    @Operation(summary = "售后详情 + 操作日志（数据权限同列表；草稿仅本人；终态可查不可改）")
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
    @Operation(summary = "撤销申请。USER/ADMIN；待审核或待仓库发货（发货前）；流程结束为已撤回")
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

    @OperateLog(module = "aftersale", action = "rollback")
    @Operation(summary = "流程回退上一节点（当前节点角色或 ADMIN），同步售后状态")
    @PostMapping("/rollback")
    public R<TicketVO> rollback(@Valid @RequestBody TicketActionDTO dto,
                                @Parameter(in = ParameterIn.HEADER, hidden = true)
                                @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                @Parameter(in = ParameterIn.HEADER, hidden = true)
                                @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                @Parameter(in = ParameterIn.HEADER, hidden = true)
                                @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole,
                dto.getUserId(), dto.getUsername(), dto.getRole());
        return R.ok(ticketService.rollback(dto, operator));
    }
}
