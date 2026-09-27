package com.yean.common.dto.process;

/**
 * 流程业务终态 / 运行中。已结束状态禁止再改。
 */
public enum ProcessBizStatus {

    RUNNING,
    DONE,
    REJECTED,
    CANCELED;

    public boolean isTerminal() {
        return this != RUNNING;
    }
}
