package com.yean.trade.ticket.start;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 扫 process_start_job(NEW) 异步 process/start。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessStartJobScheduler {

    private final ProcessStartService processStartService;

    @Scheduled(cron = "${yean.schedule.process-start.cron:0/2 * * * * ?}")
    public void dispatch() {
        int done = processStartService.dispatchDue();
        if (done > 0) {
            log.info("process-start cron done={}", done);
        }
    }
}
