package com.yean.aftersale.aftersale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.aftersale.aftersale.dto.AftersaleOrder;
import com.yean.aftersale.aftersale.dto.AftersaleQueryDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AftersaleOrderMapper extends BaseMapper<AftersaleOrder> {

    AftersaleOrder selectByAftersaleNo(@Param("aftersaleNo") String aftersaleNo);

    AftersaleOrder selectByOrderNoAndSkuId(@Param("orderNo") String orderNo, @Param("skuId") Long skuId);

    List<AftersaleOrder> selectListByQuery(AftersaleQueryDTO query);
}
