package com.yean.trade.message.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("local_message")
public class LocalMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizId;
    private String topic;
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
