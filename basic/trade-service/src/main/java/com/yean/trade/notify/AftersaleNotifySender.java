package com.yean.trade.notify;

import com.yean.common.dto.notify.NotifySendDTO;
import com.yean.common.result.R;
import com.yean.common.rpc.NotifyFeignClient;
import com.yean.trade.notify.config.NotifyProperties;
import com.yean.trade.notify.dto.AftersaleNotifyMessage;
import com.yean.trade.ticket.enums.TicketAction;
import com.yean.trade.ticket.enums.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装站内信并同步调 portal（MQ Consumer 与 sync 对照模式共用）。
 */
@Component
@RequiredArgsConstructor
public class AftersaleNotifySender {

    private final NotifyFeignClient notifyFeignClient;
    private final NotifyProperties notifyProperties;

    public void sendToPortal(AftersaleNotifyMessage message) {
        for (NotifySendDTO dto : buildNotifyList(message)) {
            R.unwrap(notifyFeignClient.send(dto));
        }
    }

    public List<NotifySendDTO> buildNotifyList(AftersaleNotifyMessage msg) {
        String no = msg.getAftersaleNo();
        String sku = StringUtils.hasText(msg.getSkuName()) ? msg.getSkuName() : "商品";
        List<NotifySendDTO> list = new ArrayList<>(2);

        if ("TIMEOUT_CLOSE".equalsIgnoreCase(msg.getEvent())) {
            list.add(inbox(msg.getUserId(), "售后已超时关闭",
                    "您的售后单 " + no + " 因超过处理时限未办结，已自动关闭。"));
            return list;
        }

        TicketAction action = parseAction(msg.getEvent());
        switch (action) {
            case APPLY:
                list.add(inbox(msg.getMerchantId(), "新售后待审核",
                        "售后单 " + no + "（" + sku + "）待您审核。"));
                break;
            case APPROVE:
                list.add(inbox(msg.getUserId(), "售后审核通过",
                        "您的售后单 " + no + " 已通过商家审核。"));
                if (msg.getStatus() != null && msg.getStatus() == TicketStatus.WAIT_SHIP.getCode()) {
                    list.add(inbox(notifyProperties.getWarehouseUserId(), "售后待仓库发货",
                            "售后单 " + no + "（" + sku + "）待发货。"));
                }
                break;
            case REJECT:
                list.add(inbox(msg.getUserId(), "售后已驳回",
                        "您的售后单 " + no + " 已被驳回。"));
                break;
            case WITHDRAW:
                list.add(inbox(msg.getMerchantId(), "用户已撤回售后",
                        "售后单 " + no + " 已被用户撤回。"));
                break;
            case SHIP:
                list.add(inbox(msg.getUserId(), "仓库已发货",
                        "售后单 " + no + " 仓库已发货，请确认收货。"));
                break;
            case RECEIVE:
                if (msg.getStatus() != null && msg.getStatus() == TicketStatus.WAIT_REFUND.getCode()) {
                    list.add(inbox(msg.getMerchantId(), "用户已收货，待退款",
                            "售后单 " + no + " 用户已确认收货，请处理退款。"));
                } else {
                    list.add(inbox(msg.getUserId(), "售后已办结",
                            "您的售后单 " + no + " 已办结。"));
                }
                break;
            case REFUND:
                list.add(inbox(msg.getUserId(), "退款已完成",
                        "您的售后单 " + no + " 退款已办结。"));
                break;
            case ROLLBACK:
                list.add(inbox(msg.getUserId(), "售后流程已回退",
                        "您的售后单 " + no + " 已回退至上一节点。"));
                break;
            default:
                break;
        }
        return list;
    }

    private static TicketAction parseAction(String event) {
        if (!StringUtils.hasText(event)) {
            throw new IllegalArgumentException("empty notify event");
        }
        return TicketAction.valueOf(event.trim().toUpperCase());
    }

    private static NotifySendDTO inbox(Long userId, String title, String content) {
        if (userId == null) {
            throw new IllegalArgumentException("notify userId is null, title=" + title);
        }
        NotifySendDTO dto = new NotifySendDTO();
        dto.setUserId(userId);
        dto.setTitle(title);
        dto.setContent(content);
        dto.setChannel("INBOX");
        dto.setBizType("AFTERSALE");
        return dto;
    }
}
