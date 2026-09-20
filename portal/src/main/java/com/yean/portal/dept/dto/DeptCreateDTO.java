package com.yean.portal.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "新增部门请求")
public class DeptCreateDTO {

    @NotBlank(message = "部门名称不能为空")
    @Schema(description = "部门名称", example = "运营中心", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "父部门不能为空")
    @Schema(description = "父部门 ID，根部门传 0", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long parentId;
}
