package com.yean.trade.order.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.trade.order.dto.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
@DS("order")
public interface OrderMapper extends BaseMapper<Order> {

    Order selectByOrderNo(@Param("orderNo") String orderNo);

    IPage<Order> selectByUserId(IPage<Order> page, @Param("userId") Long userId);
}
