package com.yean.common.dto.process;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ProcessStartDTO {

    @NotBlank
    private String businessKey;

    /** return / exchange / refund */
    @NotBlank
    private String processDefKey;

    @NotNull
    private Long applicantUserId;

    private String operator;
    private String role;
    private String remark;
}
