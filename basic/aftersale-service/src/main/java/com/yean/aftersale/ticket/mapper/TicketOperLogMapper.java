package com.yean.aftersale.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.aftersale.ticket.dto.TicketOperLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketOperLogMapper extends BaseMapper<TicketOperLog> {

    List<TicketOperLog> selectByAftersaleNo(@Param("aftersaleNo") String aftersaleNo);
}
