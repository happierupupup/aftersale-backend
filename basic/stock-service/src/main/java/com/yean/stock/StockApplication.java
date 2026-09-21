package com.yean.stock;

import com.yean.common.operatelog.OperateLogFeignClient;
import com.yean.stock.config.StockProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(clients = OperateLogFeignClient.class)
@EnableConfigurationProperties(StockProperties.class)
@EnableDiscoveryClient
@SpringBootApplication
public class StockApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockApplication.class, args);
    }
}
