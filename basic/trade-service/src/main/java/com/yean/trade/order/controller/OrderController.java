package com.yean.trade.order.controller;

import com.yean.common.operator.Operator;
import com.yean.common.operator.OperatorResolver;
import com.yean.common.operatelog.OperateLog;
import com.yean.common.order.OrderVO;
import com.yean.common.result.PageQuery;
import com.yean.common.result.PageResult;
import com.yean.common.result.R;
import com.yean.trade.order.dto.OrderBatchResultVO;
import com.yean.trade.order.dto.OrderCreateDTO;
import com.yean.trade.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

@Tag(name = "模拟订单")
@Validated
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OperatorResolver operatorResolver;

    @Operation(summary = "当前用户订单分页。page/size 默认 1/10，size 上限 50")
    @GetMapping("/list")
    public R<PageResult<OrderVO>> list(@RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                       @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                       @RequestHeader(value = "X-Role", required = false) String headerRole,
                                       @Parameter(description = "仅本地调试") @RequestParam(required = false) Long userId,
                                       PageQuery pageQuery) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, userId, null, null);
        PageQuery pq = pageQuery == null ? new PageQuery() : pageQuery;
        return R.ok(orderService.listByUserId(operator.getUserId(), pq.pageOrDefault(), pq.sizeOrDefault()));
    }

    @OperateLog(module = "order", action = "create")
    @Operation(summary = "新增订单。orderNo 可空自动生成；userId 空则取当前用户；status/payStatus 默认 1/1")
    @PostMapping
    public R<OrderVO> create(@Valid @RequestBody OrderCreateDTO dto,
                             @Parameter(in = ParameterIn.HEADER, hidden = true)
                             @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                             @Parameter(in = ParameterIn.HEADER, hidden = true)
                             @RequestHeader(value = "X-Username", required = false) String headerUsername,
                             @Parameter(in = ParameterIn.HEADER, hidden = true)
                             @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        return R.ok(orderService.create(dto, operator));
    }

    @OperateLog(module = "order", action = "upload")
    @Operation(summary = "CSV 批量导入订单。表头: orderNo,userId,merchantId,skuId,skuName,num,amount,status,payStatus；"
            + "orderNo/status/payStatus/merchantId 可空；单行失败不中断，最多 2000 行")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<OrderBatchResultVO> upload(@RequestPart("file") MultipartFile file,
                                        @Parameter(in = ParameterIn.HEADER, hidden = true)
                                        @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                        @Parameter(in = ParameterIn.HEADER, hidden = true)
                                        @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                        @Parameter(in = ParameterIn.HEADER, hidden = true)
                                        @RequestHeader(value = "X-Role", required = false) String headerRole) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, null, null, null);
        return R.ok(orderService.upload(file, operator));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public R<OrderVO> get(@PathVariable String orderNo) {
        return R.ok(orderService.getByOrderNo(orderNo));
    }
}
