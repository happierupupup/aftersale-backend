package com.yean.common.dto.process;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ProcessActionDTO {

    @NotBlank
    private String businessKey;

    /** complete 时期望的当前 taskDefinitionKey，用于防串节点 */
    private String expectedTaskKey;

    private Long userId;
    private String operator;
    private String role;
    private String remark;
}
