package com.yean.trade.ticket.start;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_start_job")
public class ProcessStartJob {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String aftersaleNo;
    private String processDefKey;
    private Long applicantUserId;
    private String operatorName;
    private String operatorRole;
    private String remark;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
