package com.yean.trade.ticket.controller;

import com.yean.common.result.R;
import com.yean.trade.ticket.job.PendingRemindService;
import com.yean.trade.ticket.job.TimeoutCloseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@Tag(name = "售后定时任务")
@RestController
@RequestMapping("/aftersale/job")
@RequiredArgsConstructor
public class TicketJobController {

    private final PendingRemindService pendingRemindService;
    private final TimeoutCloseService timeoutCloseService;

    @Operation(summary = "手动触发：未处理售后催办（与每天 10 点定时同一逻辑）")
    @PostMapping("/pending-remind")
    public R<Map<String, Integer>> pendingRemind() {
        int sent = pendingRemindService.remindPending();
        return R.ok(Collections.singletonMap("notifySent", sent));
    }

    @Operation(summary = "手动触发：超时未办结自动驳回（与定时同一逻辑）")
    @PostMapping("/timeout")
    public R<Map<String, Integer>> timeoutClose() {
        int closed = timeoutCloseService.closeTimedOut();
        return R.ok(Collections.singletonMap("closed", closed));
    }
}
