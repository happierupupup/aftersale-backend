package com.yean.aftersale.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yean.aftersale.order.dto.OrderInfo;
import com.yean.aftersale.order.dto.OrderVO;
import com.yean.aftersale.order.mapper.OrderMapper;
import com.yean.aftersale.order.service.OrderService;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    @Override
    public OrderVO getByOrderNo(String orderNo) {
        OrderInfo order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorEnum.ORDER_NOT_FOUND);
        }
        return toVO(order);
    }

    @Override
    public PageResult<OrderVO> listByUserId(Long userId, long page, long size) {
        IPage<OrderInfo> result = orderMapper.selectByUserId(new Page<>(page, size), userId);
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
    }

    private OrderVO toVO(OrderInfo order) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setMerchantId(order.getMerchantId());
        vo.setSkuId(order.getSkuId());
        vo.setSkuName(order.getSkuName());
        vo.setNum(order.getNum());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setPayStatus(order.getPayStatus());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }
}
