package com.yean.aftersale;

import com.yean.common.operatelog.OperateLogFeignClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(clients = OperateLogFeignClient.class)
@EnableDiscoveryClient
@SpringBootApplication
public class AftersaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(AftersaleApplication.class, args);
    }
}
