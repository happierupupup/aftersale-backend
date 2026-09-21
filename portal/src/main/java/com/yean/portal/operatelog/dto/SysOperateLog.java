package com.yean.portal.operatelog.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operate_log")
public class SysOperateLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String action;
    private String method;
    private String path;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private Integer success;
    private Integer costMs;
    private String remark;
    private LocalDateTime createTime;
}
