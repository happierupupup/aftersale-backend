package com.yean.portal.notify.controller;

import com.yean.common.result.R;
import com.yean.portal.notify.dto.NotifyQueryDTO;
import com.yean.portal.notify.dto.NotifySendDTO;
import com.yean.portal.notify.dto.NotifyVO;
import com.yean.portal.notify.service.NotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "通知")
@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @Operation(summary = "发送通知（站内信落库；短信/邮件只打日志）")
    @PostMapping("/send")
    public R<Long> send(@Valid @RequestBody NotifySendDTO dto) {
        return R.ok(notifyService.send(dto));
    }

    @Operation(summary = "通知列表")
    @GetMapping("/list")
    public R<List<NotifyVO>> list(NotifyQueryDTO query) {
        return R.ok(notifyService.list(query));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notifyService.markRead(id);
        return R.ok();
    }
}
