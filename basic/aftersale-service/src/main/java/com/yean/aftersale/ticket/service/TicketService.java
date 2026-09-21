package com.yean.aftersale.ticket.service;

import com.yean.aftersale.ticket.dto.TicketActionDTO;
import com.yean.aftersale.ticket.dto.TicketApplyDTO;
import com.yean.aftersale.ticket.dto.TicketQueryDTO;
import com.yean.aftersale.ticket.dto.TicketVO;
import com.yean.common.operator.Operator;

import java.util.List;

public interface TicketService {

    TicketVO apply(TicketApplyDTO dto, Operator operator);

    List<TicketVO> page(TicketQueryDTO query, Operator operator);

    TicketVO detail(String aftersaleNo, Operator operator);

    TicketVO approve(TicketActionDTO dto, Operator operator);

    TicketVO reject(TicketActionDTO dto, Operator operator);

    TicketVO withdraw(TicketActionDTO dto, Operator operator);
}
