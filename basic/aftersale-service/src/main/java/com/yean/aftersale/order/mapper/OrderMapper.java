package com.yean.aftersale.order.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.aftersale.order.dto.OrderInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
@DS("order")
public interface OrderMapper extends BaseMapper<OrderInfo> {

    OrderInfo selectByOrderNo(@Param("orderNo") String orderNo);

    IPage<OrderInfo> selectByUserId(IPage<OrderInfo> page, @Param("userId") Long userId);
}
