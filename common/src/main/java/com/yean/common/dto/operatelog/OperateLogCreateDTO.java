package com.yean.common.dto.operatelog;

import lombok.Data;

@Data
public class OperateLogCreateDTO {

    private String module;
    private String action;
    private String method;
    private String path;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private Boolean success;
    private Integer costMs;
    private String remark;
}
