package com.yean.trade.ticket.compensate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 扫 process_compensate(NEW) 重试流程 rollback。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessCompensateJob {

    private final ProcessCompensateService processCompensateService;

    @Scheduled(cron = "${yean.schedule.process-compensate.cron:0/10 * * * * ?}")
    public void dispatch() {
        int done = processCompensateService.dispatchDue();
        if (done > 0) {
            log.info("process-compensate cron done={}", done);
        }
    }
}
