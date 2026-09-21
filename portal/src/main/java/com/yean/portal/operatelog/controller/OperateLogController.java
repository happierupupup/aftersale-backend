package com.yean.portal.operatelog.controller;

import com.yean.common.operatelog.OperateLog;
import com.yean.common.operatelog.OperateLogCreateDTO;
import com.yean.common.result.R;
import com.yean.portal.operatelog.service.OperateLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "操作日志")
@Validated
@RestController
@RequestMapping("/operatelog")
@RequiredArgsConstructor
public class OperateLogController {

    private final OperateLogService operateLogService;

    @Operation(summary = "新增操作日志（供网关 / 各服务 Feign 异步写入）")
    @PostMapping
    public R<Void> create(@RequestBody OperateLogCreateDTO dto) {
        operateLogService.create(dto);
        return R.ok();
    }
}
