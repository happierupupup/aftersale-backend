package com.yean.process.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_biz")
public class ProcessBiz {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessKey;
    private String processInstanceId;
    private String processDefKey;
    private String bizStatus;
    private String currentTaskKey;
    private Long applicantUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
