package com.yean.trade;

import com.yean.common.operatelog.OperateLogFeignClient;
import com.yean.trade.config.SkuCacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 交易业务：order / ticket（售后）/ sku / inventory（库存）。
 */
@EnableFeignClients(clients = OperateLogFeignClient.class)
@EnableConfigurationProperties(SkuCacheProperties.class)
@EnableDiscoveryClient
@SpringBootApplication
public class TradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeApplication.class, args);
    }
}
