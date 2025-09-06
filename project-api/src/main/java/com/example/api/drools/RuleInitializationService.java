package com.example.api.drools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 规则初始化服务
 * 在应用启动时自动从数据库加载所有启用的规则
 */
@Component
public class RuleInitializationService implements ApplicationRunner {
    
    @Autowired
    private KieManager kieManager;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("正在从数据库加载规则...");
        kieManager.loadAllRulesFromDatabase();
        System.out.println("规则加载完成");
    }
}
