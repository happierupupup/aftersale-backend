package com.yean.trade.message.controller;

import com.yean.common.result.R;
import com.yean.trade.message.service.LocalMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@Tag(name = "本地消息 outbox")
@RestController
@RequestMapping("/message/job")
@RequiredArgsConstructor
public class MessageJobController {

    private final LocalMessageService localMessageService;

    @Operation(summary = "手动触发：扫 local_message 投递 MQ（与定时同一逻辑）")
    @PostMapping("/dispatch")
    public R<Map<String, Integer>> dispatch() {
        int sent = localMessageService.dispatchDue();
        return R.ok(Collections.singletonMap("sent", sent));
    }
}
