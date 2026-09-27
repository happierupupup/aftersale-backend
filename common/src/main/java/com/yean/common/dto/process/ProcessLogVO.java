package com.yean.common.dto.process;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessLogVO {

    private String action;
    private String taskKey;
    private String fromTaskKey;
    private String toTaskKey;
    private String operator;
    private String role;
    private String remark;
    private LocalDateTime createTime;
}
