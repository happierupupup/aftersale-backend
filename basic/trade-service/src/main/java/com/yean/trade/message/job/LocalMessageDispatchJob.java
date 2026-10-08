package com.yean.trade.message.job;

import com.yean.trade.message.service.LocalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 扫 local_message(NEW) 投递 RocketMQ（阶段 8b outbox）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalMessageDispatchJob {

    private final LocalMessageService localMessageService;

    @Scheduled(cron = "${yean.schedule.local-message.cron:0/5 * * * * ?}")
    public void dispatch() {
        int sent = localMessageService.dispatchDue();
        if (sent > 0) {
            log.info("local-message cron dispatched={}", sent);
        }
    }
}
