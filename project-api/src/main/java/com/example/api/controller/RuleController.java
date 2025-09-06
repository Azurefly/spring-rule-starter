package com.example.api.controller;

import com.example.api.service.RuleService;
import com.example.api.entity.RuleMeta;
import com.example.common.Result;
import com.example.api.drools.KieManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
public class RuleController {
    private static final Logger logger = LoggerFactory.getLogger(RuleController.class);

    @Autowired
    private RuleService ruleService;
    
    public RuleController() {
        System.out.println("=== RuleController constructor called ===");
        logger.info("=== RuleController constructor called ===");
    }
    
    @PostConstruct
    public void init() {
        System.out.println("=== RuleController @PostConstruct called ===");
        logger.info("=== RuleController @PostConstruct called ===");
        logger.info("=== RuleController initialized with mapping: /api/rules ===");
    }

    @Autowired
    private KieManager kieManager;

    @PostMapping("/upload")
    public Result<Void> uploadRule(@RequestParam String name,
                                   @RequestParam(defaultValue = "DROOLS") String type,
                                   @RequestParam MultipartFile file) throws Exception {
        try {
            return ruleService.saveRule(name, type, file);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/health/test")
    public String test() {
        System.out.println("=== TEST ENDPOINT CALLED ===");
        System.err.println("=== TEST ENDPOINT CALLED ===");
        logger.error("=== TEST ENDPOINT CALLED ===");
        logger.info("=== Processing GET request to /api/rules/health/test ===");
        return "TEST SUCCESS";
    }

    @GetMapping("/list")
    public ResponseEntity<Result<List<RuleMeta>>> list() {
        System.out.println("=== RuleController.list called ===");
        System.err.println("=== RuleController.list called ===");
        logger.error("=== RuleController.list called ===");
        try {
            return ResponseEntity.ok(ruleService.listRules());
        } catch (Exception e) {
            return ResponseEntity.ok(Result.fail(e.getMessage()));
        }
    }

    @PostMapping("/exec/{name}")
    public Result<Object> exec(@PathVariable String name, @RequestBody Map<String,Object> fact) throws Exception {
        try {
            return ruleService.executeByName(name, fact);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/refresh/{name}")
    public Result<Void> refresh(@PathVariable String name) throws Exception {
        System.out.println("=== RuleController.refresh called for: " + name + " ===");
        System.err.println("=== RuleController.refresh called for: " + name + " ===");
        logger.error("=== RuleController.refresh called for: {} ===", name);
        try {
            System.out.println("=== About to call kieManager.reloadRuleFromDatabase ===");
            System.err.println("=== About to call kieManager.reloadRuleFromDatabase ===");
            logger.error("=== About to call kieManager.reloadRuleFromDatabase ===");
            // 从数据库重新加载指定规则
            com.example.api.drools.BuildResult result = kieManager.reloadRuleFromDatabase(name);
            System.out.println("=== kieManager.reloadRuleFromDatabase returned: " + result.getStatus() + " ===");
            System.err.println("=== kieManager.reloadRuleFromDatabase returned: " + result.getStatus() + " ===");
            logger.error("=== kieManager.reloadRuleFromDatabase returned: {} ===", result.getStatus());
            if (result.getStatus() == com.example.api.drools.BuildResult.Status.SUCCESS) {
                return Result.success();
            } else {
                return Result.fail("Failed to refresh rule: " + result.getMessage());
            }
        } catch (Exception e) {
            System.out.println("=== Exception in refresh: " + e.getMessage() + " ===");
            System.err.println("=== Exception in refresh: " + e.getMessage() + " ===");
            logger.error("=== Exception in refresh: {} ===", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }
    
    @PostMapping("/reload-all")
    public Result<Void> reloadAllRules() {
        try {
            // 重新加载所有规则
            kieManager.loadAllRulesFromDatabase();
            return Result.success();
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/rule-content/{name}")
    public Result<String> getRuleContent(@PathVariable String name) {
        try {
            Result<com.example.api.entity.RuleMeta> result = ruleService.getRule(name);
            if (result != null && result.getData() != null) {
                return Result.success(result.getData().getContent());
            }
            return Result.fail("Rule not found");
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/{name}")
    public Result<Void> updateRule(@PathVariable String name, @RequestBody String content) throws Exception {
        try {
            return ruleService.updateRuleContent(name, content);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/meta/{name}")
    public Result<com.example.api.entity.RuleMeta> meta(@PathVariable String name) {
        try {
            return ruleService.getRule(name);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
