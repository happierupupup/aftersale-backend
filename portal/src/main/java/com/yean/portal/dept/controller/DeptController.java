package com.yean.portal.dept.controller;

import com.yean.portal.dept.dto.DeptCreateDTO;
import com.yean.portal.dept.dto.DeptQueryDTO;
import com.yean.portal.dept.dto.DeptUpdateDTO;
import com.yean.portal.dept.dto.DeptVO;
import com.yean.portal.dept.service.DeptService;
import com.yean.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "部门")
@RestController
@RequestMapping("/dept")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @Operation(summary = "部门树")
    @GetMapping("/tree")
    public R<List<DeptVO>> tree() {
        return R.ok(deptService.tree());
    }

    @Operation(summary = "部门列表")
    @GetMapping("/list")
    public R<List<DeptVO>> list(DeptQueryDTO query) {
        return R.ok(deptService.list(query));
    }

    @Operation(summary = "按 ID 查询部门（含下级子树）")
    @GetMapping("/{id:\\d+}")
    public R<DeptVO> get(@PathVariable Long id) {
        return R.ok(deptService.getById(id));
    }

    @Operation(summary = "新增部门")
    @PostMapping
    public R<Long> create(@Valid @RequestBody DeptCreateDTO dto) {
        return R.ok(deptService.create(dto));
    }

    @Operation(summary = "修改部门")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody DeptUpdateDTO dto) {
        deptService.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return R.ok();
    }
}
