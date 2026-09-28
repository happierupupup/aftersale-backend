package com.yean.trade;

import com.yean.common.rpc.NotifyFeignClient;
import com.yean.common.rpc.OperateLogFeignClient;
import com.yean.common.rpc.ProcessFeignClient;
import com.yean.trade.config.SkuCacheProperties;
import com.yean.trade.message.config.LocalMessageProperties;
import com.yean.trade.notify.config.NotifyProperties;
import com.yean.trade.ticket.job.PendingRemindProperties;
import com.yean.trade.ticket.job.TimeoutCloseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 交易业务：order / ticket（售后）/ sku / inventory（库存）。流程经 ProcessFeignClient → process-service。
 */
@EnableScheduling
@EnableFeignClients(clients = {OperateLogFeignClient.class, ProcessFeignClient.class, NotifyFeignClient.class})
@EnableConfigurationProperties({
        SkuCacheProperties.class,
        NotifyProperties.class,
        PendingRemindProperties.class,
        LocalMessageProperties.class,
        TimeoutCloseProperties.class
})
@EnableDiscoveryClient
@SpringBootApplication
public class TradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeApplication.class, args);
    }
}
