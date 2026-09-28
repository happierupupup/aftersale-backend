package com.yean.common.dto.notify;

import lombok.Data;

/**
 * 调 portal /notify/send 的请求体（与 portal NotifySendDTO 字段对齐）。
 */
@Data
public class NotifySendDTO {

    private Long userId;
    private String title;
    private String content;
    private String channel;
    private String bizType;
}
