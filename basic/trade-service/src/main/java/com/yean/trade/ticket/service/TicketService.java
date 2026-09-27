package com.yean.trade.ticket.service;

import com.yean.trade.ticket.dto.TicketActionDTO;
import com.yean.trade.ticket.dto.TicketApplyDTO;
import com.yean.trade.ticket.dto.TicketQueryDTO;
import com.yean.trade.ticket.dto.TicketVO;
import com.yean.common.dto.Operator;
import com.yean.common.result.PageResult;

public interface TicketService {

    TicketVO apply(TicketApplyDTO dto, Operator operator);

    PageResult<TicketVO> page(TicketQueryDTO query, Operator operator);

    TicketVO detail(String aftersaleNo, Operator operator);

    TicketVO approve(TicketActionDTO dto, Operator operator);

    TicketVO reject(TicketActionDTO dto, Operator operator);

    TicketVO withdraw(TicketActionDTO dto, Operator operator);

    /** 仓库发货：待仓库发货 → 待用户收货 */
    TicketVO ship(TicketActionDTO dto, Operator operator);

    /** 用户确认收货：待用户收货 → 待退款 */
    TicketVO receive(TicketActionDTO dto, Operator operator);

    /** 商家退款办结：待退款 → 已办结（换货收货后已办结，无需本接口） */
    TicketVO refund(TicketActionDTO dto, Operator operator);

    /** 流程回退到上一 userTask，并同步售后状态 */
    TicketVO rollback(TicketActionDTO dto, Operator operator);
}
