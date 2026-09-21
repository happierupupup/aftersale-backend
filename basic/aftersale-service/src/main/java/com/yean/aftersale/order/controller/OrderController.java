package com.yean.aftersale.order.controller;

import com.yean.aftersale.order.dto.OrderVO;
import com.yean.aftersale.order.service.OrderService;
import com.yean.common.operator.Operator;
import com.yean.common.operator.OperatorResolver;
import com.yean.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "模拟订单")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OperatorResolver operatorResolver;

    @Operation(summary = "当前用户订单列表。本地可传 userId；正式取 X-User-Id")
    @GetMapping("/list")
    public R<List<OrderVO>> list(@RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                 @RequestHeader(value = "X-Username", required = false) String headerUsername,
                                 @RequestHeader(value = "X-Role", required = false) String headerRole,
                                 @Parameter(description = "仅本地调试") @RequestParam(required = false) Long userId) {
        Operator operator = operatorResolver.resolve(headerUserId, headerUsername, headerRole, userId, null, null);
        return R.ok(orderService.listByUserId(operator.getUserId()));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public R<OrderVO> get(@PathVariable String orderNo) {
        return R.ok(orderService.getByOrderNo(orderNo));
    }
}
