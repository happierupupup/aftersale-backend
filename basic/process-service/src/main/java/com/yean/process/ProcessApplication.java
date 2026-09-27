package com.yean.process;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = "com.yean",
        exclude = SecurityAutoConfiguration.class)
@MapperScan("com.yean.process.mapper")
public class ProcessApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessApplication.class, args);
    }
}
