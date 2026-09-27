package com.yean.common.dto.process;

import lombok.Data;

@Data
public class ProcessTaskVO {

    private String taskId;
    private String taskDefinitionKey;
    private String taskName;
    private String candidateGroup;
}
