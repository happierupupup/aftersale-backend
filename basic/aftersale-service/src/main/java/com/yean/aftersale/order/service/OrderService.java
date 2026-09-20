package com.yean.aftersale.order.service;

import com.yean.aftersale.order.dto.OrderVO;

import java.util.List;

public interface OrderService {

    OrderVO getByOrderNo(String orderNo);

    List<OrderVO> listByUserId(Long userId);
}
