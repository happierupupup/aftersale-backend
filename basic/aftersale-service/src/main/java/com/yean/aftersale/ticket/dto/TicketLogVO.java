package com.yean.aftersale.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "售后操作日志")
public class TicketLogVO {

    @Schema(description = "操作 APPLY/APPROVE/REJECT/WITHDRAW")
    private String action;

    @Schema(description = "操作人 ID")
    private Long operatorId;

    @Schema(description = "操作人名称")
    private String operatorName;

    @Schema(description = "变更前状态")
    private Integer beforeStatus;

    @Schema(description = "变更后状态")
    private Integer afterStatus;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}
