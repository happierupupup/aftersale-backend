package com.yean.trade.ticket.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天 2:30 超时关单（与 10:00 催办独立；cron 可配，时区 Asia/Shanghai）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutCloseJob {

    private final TimeoutCloseService timeoutCloseService;

    @Scheduled(cron = "${yean.schedule.timeout-close.cron:0 30 2 * *?}", zone = "Asia/Shanghai")
    public void closeAtNight() {
        int closed = timeoutCloseService.closeTimedOut();
        log.info("timeout close cron finished, closed={}", closed);
    }
}
