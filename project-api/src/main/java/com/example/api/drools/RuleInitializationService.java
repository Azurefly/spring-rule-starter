package com.example.api.drools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Loads all enabled rules after the application context is ready. */
@Component
public class RuleInitializationService implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(RuleInitializationService.class);

    @Autowired
    private KieManager kieManager;

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Loading enabled rules from database");
        kieManager.loadAllRulesFromDatabase();
    }
}
