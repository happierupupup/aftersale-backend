package com.yean.trade.ticket.strategy;

import com.yean.common.dto.Operator;
import com.yean.common.dto.process.ProcessInstanceVO;
import com.yean.trade.ticket.dto.LogisticsInfo;
import com.yean.trade.ticket.dto.RefundDetail;
import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.dto.TicketActionDTO;
import com.yean.trade.ticket.enums.TicketAction;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TicketNodeContext {

    private final Ticket row;
    private final TicketActionDTO dto;
    private final Operator operator;
    private final TicketAction action;
    private final ProcessInstanceVO process;
    private final LogisticsInfo logistics;
    private final RefundDetail refundDetail;
}
