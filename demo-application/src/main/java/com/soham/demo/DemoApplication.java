package com.soham.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo application proving that {@code rate-limiter-spring-boot-starter}
 * works as a plain dependency: no rate limiter beans are declared anywhere
 * in this module — they all arrive automatically via the starter's
 * auto-configuration.
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
