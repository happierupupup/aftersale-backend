package com.yean.portal.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改部门请求，空字段表示不修改")
public class DeptUpdateDTO {

    @Schema(description = "部门名称", example = "商家组")
    private String name;

    @Schema(description = "父部门 ID，根部门传 0；不能指向自己", example = "2")
    private Long parentId;
}
