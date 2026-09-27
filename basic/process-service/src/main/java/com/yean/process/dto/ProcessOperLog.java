package com.yean.process.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_oper_log")
public class ProcessOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessKey;
    private String processInstanceId;
    private String action;
    private String taskKey;
    private String fromTaskKey;
    private String toTaskKey;
    private String operator;
    private String role;
    private String remark;
    private LocalDateTime createTime;
}
