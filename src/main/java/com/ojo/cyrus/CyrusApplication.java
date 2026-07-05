package com.ojo.cyrus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.ojo.cyrus")
public class CyrusApplication {

    static void main(String[] args) {
        SpringApplication.run(CyrusApplication.class, args);
    }
}
