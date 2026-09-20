package com.yean.aftersale.aftersale.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aftersale_oper_log")
public class AftersaleOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String aftersaleNo;
    private Long operatorId;
    private String operatorName;
    private String action;
    private Integer beforeStatus;
    private Integer afterStatus;
    private String remark;
    private LocalDateTime createTime;
}
