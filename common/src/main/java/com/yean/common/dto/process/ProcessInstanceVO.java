package com.yean.common.dto.process;

import lombok.Data;

@Data
public class ProcessInstanceVO {

    private String businessKey;
    private String processInstanceId;
    private String processDefKey;
    /** RUNNING / DONE / REJECTED / CANCELED */
    private String bizStatus;
    private boolean ended;
    private ProcessTaskVO currentTask;
    /** complete 刚完成的 taskKey（策略路由用） */
    private String completedTaskKey;
}
