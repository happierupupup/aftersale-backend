package com.yean.portal.dept.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "部门信息，树接口时 children 为子节点")
public class DeptVO {

    @Schema(description = "部门 ID", example = "1")
    private Long id;

    @Schema(description = "部门名称", example = "总公司")
    private String name;

    @Schema(description = "父部门 ID，根部门为 0", example = "0")
    private Long parentId;

    @Schema(description = "层级，根为 1，子部门在父级上加 1", example = "1")
    private Integer level;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "子部门。树接口和按 ID 查询会填充；平铺列表不返回此字段")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<DeptVO> children = new ArrayList<>();
}
