package com.example.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        scanBasePackages = {"com.example"},
        excludeName = {"org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
@EnableJpaRepositories(basePackages = {"com.example.api.repository"})
@EntityScan(basePackages = {"com.example.api.entity"})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
