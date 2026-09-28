package com.yean.trade.ticket.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天早上 10 点催办未处理售后（cron 可配，时区 Asia/Shanghai）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingRemindJob {

    private final PendingRemindService pendingRemindService;

    @Scheduled(cron = "${yean.schedule.pending-remind.cron:0 0 10 * *?}", zone = "Asia/Shanghai")
    public void remindAtTen() {
        int sent = pendingRemindService.remindPending();
        log.info("pending remind cron finished, notifySent={}", sent);
    }
}
