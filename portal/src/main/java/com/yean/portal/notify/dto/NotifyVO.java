package com.yean.portal.notify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "通知")
public class NotifyVO {

    @Schema(description = "通知 ID")
    private Long id;

    @Schema(description = "接收用户 ID")
    private Long userId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "渠道")
    private String channel;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "0 未读 1 已读")
    private Integer readFlag;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
