package com.yean.common.dto.process;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * BPMN userTask id / 候选组 / 节点顺序（与 process-service processes/*.bpmn20.xml 一致）。
 * 售后 TicketStatus 映射放在 trade 的 strategy 里，不放这里。
 */
public final class ProcessTaskKeys {

    public static final String MERCHANT_APPROVE = "merchantApprove";
    public static final String WAREHOUSE_SHIP = "warehouseShip";
    public static final String USER_RECEIVE = "userReceive";
    public static final String MERCHANT_REFUND = "merchantRefund";

    private static final Map<String, String> CANDIDATE_GROUP = Map.of(
            MERCHANT_APPROVE, "MERCHANT",
            WAREHOUSE_SHIP, "WAREHOUSE",
            USER_RECEIVE, "USER",
            MERCHANT_REFUND, "MERCHANT"
    );

    private static final Map<String, List<String>> FLOW_ORDER = Map.of(
            ProcessDefKey.RETURN.getKey(),
            List.of(MERCHANT_APPROVE, WAREHOUSE_SHIP, USER_RECEIVE, MERCHANT_REFUND),
            ProcessDefKey.EXCHANGE.getKey(),
            List.of(MERCHANT_APPROVE, WAREHOUSE_SHIP, USER_RECEIVE),
            ProcessDefKey.REFUND.getKey(),
            List.of(MERCHANT_APPROVE, MERCHANT_REFUND)
    );

    private ProcessTaskKeys() {
    }

    public static String candidateGroup(String taskKey) {
        return CANDIDATE_GROUP.get(taskKey);
    }

    public static List<String> flowOrder(String processDefKey) {
        return FLOW_ORDER.getOrDefault(processDefKey, Collections.emptyList());
    }

    public static String previousTask(String processDefKey, String currentTaskKey) {
        List<String> order = flowOrder(processDefKey);
        int idx = order.indexOf(currentTaskKey);
        if (idx <= 0) {
            return null;
        }
        return order.get(idx - 1);
    }
}
