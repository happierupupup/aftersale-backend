package com.yean.process.controller;

import com.yean.common.dto.process.ProcessActionDTO;
import com.yean.common.dto.process.ProcessHistoryVO;
import com.yean.common.dto.process.ProcessInstanceVO;
import com.yean.common.dto.process.ProcessStartDTO;
import com.yean.common.result.R;
import com.yean.process.service.ProcessEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Tag(name = "流程引擎")
@Validated
@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessEngineService processEngineService;

    @Operation(summary = "启动流程")
    @PostMapping("/start")
    public R<ProcessInstanceVO> start(@Valid @RequestBody ProcessStartDTO dto) {
        return R.ok(processEngineService.start(dto));
    }

    @Operation(summary = "完成当前节点")
    @PostMapping("/complete")
    public R<ProcessInstanceVO> complete(@Valid @RequestBody ProcessActionDTO dto) {
        return R.ok(processEngineService.complete(dto));
    }

    @Operation(summary = "驳回（结束为 REJECTED）")
    @PostMapping("/reject")
    public R<ProcessInstanceVO> reject(@Valid @RequestBody ProcessActionDTO dto) {
        return R.ok(processEngineService.reject(dto));
    }

    @Operation(summary = "撤回（结束为 CANCELED）")
    @PostMapping("/withdraw")
    public R<ProcessInstanceVO> withdraw(@Valid @RequestBody ProcessActionDTO dto) {
        return R.ok(processEngineService.withdraw(dto));
    }

    @Operation(summary = "回退到上一 userTask")
    @PostMapping("/rollback")
    public R<ProcessInstanceVO> rollback(@Valid @RequestBody ProcessActionDTO dto) {
        return R.ok(processEngineService.rollback(dto));
    }

    @Operation(summary = "当前任务与业务状态")
    @GetMapping("/{businessKey}/current")
    public R<ProcessInstanceVO> current(@PathVariable String businessKey) {
        return R.ok(processEngineService.current(businessKey));
    }

    @Operation(summary = "流程操作历史")
    @GetMapping("/{businessKey}/history")
    public R<ProcessHistoryVO> history(@PathVariable String businessKey) {
        return R.ok(processEngineService.history(businessKey));
    }
}
