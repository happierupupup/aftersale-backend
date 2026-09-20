package com.yean.aftersale.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.aftersale.order.dto.OrderInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderMapper extends BaseMapper<OrderInfo> {

    OrderInfo selectByOrderNo(@Param("orderNo") String orderNo);

    List<OrderInfo> selectByUserId(@Param("userId") Long userId);
}
