package com.yean.aftersale.order.service;

import com.yean.aftersale.order.dto.OrderVO;
import com.yean.common.result.PageResult;

public interface OrderService {

    OrderVO getByOrderNo(String orderNo);

    PageResult<OrderVO> listByUserId(Long userId, long page, long size);
}
