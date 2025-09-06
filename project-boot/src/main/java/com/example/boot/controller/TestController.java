package com.example.boot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

@RestController
@RequestMapping("/api/test")
public class TestController {
    private static final Logger logger = LoggerFactory.getLogger(TestController.class);
    
    public TestController() {
        System.out.println("=== TestController constructor called ===");
        logger.info("=== TestController constructor called ===");
    }
    
    @PostConstruct
    public void init() {
        System.out.println("=== TestController @PostConstruct called ===");
        logger.info("=== TestController @PostConstruct called ===");
    }
    
    @GetMapping("/hello")
    public String hello() {
        System.out.println("=== TestController hello endpoint called ===");
        logger.info("=== TestController hello endpoint called ===");
        return "Hello from TestController!";
    }
}