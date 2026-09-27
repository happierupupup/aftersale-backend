package com.yean.common.dto.process;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProcessHistoryVO {

    private String businessKey;
    private String processInstanceId;
    private String processDefKey;
    private String bizStatus;
    private List<ProcessLogVO> logs = new ArrayList<>();
}
