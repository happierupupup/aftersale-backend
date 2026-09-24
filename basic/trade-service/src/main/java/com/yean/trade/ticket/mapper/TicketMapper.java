package com.yean.trade.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.trade.ticket.dto.LogisticsInfo;
import com.yean.trade.ticket.dto.RefundDetail;
import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.dto.TicketQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    Ticket selectByAftersaleNo(@Param("aftersaleNo") String aftersaleNo);

    /** 查进行中的单（active_flag = 1）；驳回/撤回的终态单不占用 (orderNo, skuId) 申请位 */
    Ticket selectActiveByOrderNoAndSkuId(@Param("orderNo") String orderNo, @Param("skuId") Long skuId);

    IPage<Ticket> selectListByQuery(IPage<Ticket> page, @Param("q") TicketQueryDTO query);

    /** 更新状态；releaseActive=true 时把 active_flag 置 NULL（驳回/撤回，允许重新申请） */
    int updateStatus(@Param("id") Long id,
                     @Param("status") int status,
                     @Param("releaseActive") boolean releaseActive,
                     @Param("logisticsInfo") LogisticsInfo logisticsInfo,
                     @Param("refundDetail") RefundDetail refundDetail);
}
