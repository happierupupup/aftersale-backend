package com.yean.aftersale.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.aftersale.ticket.dto.Ticket;
import com.yean.aftersale.ticket.dto.TicketQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    Ticket selectByAftersaleNo(@Param("aftersaleNo") String aftersaleNo);

    /** 查进行中的单（active_flag = 1）；驳回/撤回的终态单不占用 (orderNo, skuId) 申请位 */
    Ticket selectActiveByOrderNoAndSkuId(@Param("orderNo") String orderNo, @Param("skuId") Long skuId);

    List<Ticket> selectListByQuery(TicketQueryDTO query);

    /** 更新状态；releaseActive=true 时把 active_flag 置 NULL（驳回/撤回，允许重新申请） */
    int updateStatus(@Param("id") Long id, @Param("status") int status, @Param("releaseActive") boolean releaseActive);
}
