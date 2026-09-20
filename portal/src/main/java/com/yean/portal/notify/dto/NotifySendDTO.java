package com.yean.portal.notify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "发送通知")
public class NotifySendDTO {

    @NotNull(message = "接收用户不能为空")
    @Schema(description = "接收用户 ID", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "标题", example = "售后审核通过", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "正文不能为空")
    @Schema(description = "正文", example = "您的售后单已通过商家审核", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "渠道：INBOX 站内信 / SMS 短信 / MAIL 邮件，默认 INBOX", example = "INBOX")
    private String channel;

    @Schema(description = "业务类型：SYSTEM / AFTERSALE / STOCK", example = "AFTERSALE")
    private String bizType;
}
