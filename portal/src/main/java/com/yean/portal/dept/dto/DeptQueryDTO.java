package com.yean.portal.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "部门列表查询条件，均为可选")
public class DeptQueryDTO {

    @Schema(description = "部门名称，模糊匹配", example = "运营")
    private String name;

    @Schema(description = "父部门 ID，精确匹配；查根节点传 0", example = "1")
    private Long parentId;
}
