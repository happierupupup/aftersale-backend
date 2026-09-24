package com.yean.portal.notify.dto;

import com.yean.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "通知列表查询，均为可选")
public class NotifyQueryDTO extends PageQuery {

    @Schema(description = "接收用户 ID", example = "5")
    private Long userId;

    @Schema(description = "渠道 INBOX / SMS / MAIL", example = "INBOX")
    private String channel;

    @Schema(description = "0 未读 1 已读", example = "0")
    private Integer readFlag;
}
