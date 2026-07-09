package com.ojo.cyrus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.ojo.cyrus")
@EnableAsync
@EnableScheduling
@EnableCaching
public class CyrusApplication {

    static void main(String[] args) {
        SpringApplication.run(CyrusApplication.class, args);
    }
}
