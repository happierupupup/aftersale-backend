package com.yean.trade.ticket.service;

import com.yean.trade.ticket.dto.TicketActionDTO;
import com.yean.trade.ticket.dto.TicketApplyDTO;
import com.yean.trade.ticket.dto.TicketDraftDTO;
import com.yean.trade.ticket.dto.TicketQueryDTO;
import com.yean.trade.ticket.dto.TicketVO;
import com.yean.common.dto.Operator;
import com.yean.common.result.PageResult;

import java.util.List;

public interface TicketService {

    TicketVO apply(TicketApplyDTO dto, Operator operator);

    /** 保存草稿（同用户+订单+SKU upsert）；不启流程、不占进行中位 */
    TicketVO saveDraft(TicketDraftDTO dto, Operator operator);

    /** 当前用户草稿列表（前端进申请页可查 draft=true） */
    List<TicketVO> listMyDrafts(Operator operator);

    /** 按订单+SKU 取当前用户草稿，无则 null */
    TicketVO getMyDraft(String orderNo, Long skuId, Operator operator);

    /** 删除草稿 */
    void deleteDraft(String aftersaleNo, Operator operator);

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

    /**
     * 系统超时关单：结束流程并置已驳回、释放申请位、入队驳回通知。
     * 操作人记为 SYSTEM / ADMIN。
     */
    TicketVO timeoutReject(String aftersaleNo, String remark);
}
