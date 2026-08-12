package com.example.api.controller;

import com.example.api.drools.BuildResult;
import com.example.api.drools.KieManager;
import com.example.api.entity.RuleMeta;
import com.example.api.service.RuleService;
import com.example.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    @Autowired
    private RuleService ruleService;

    @Autowired
    private KieManager kieManager;

    @PostMapping("/upload")
    public Result<Void> uploadRule(@RequestParam String name,
                                   @RequestParam(defaultValue = "DROOLS") String type,
                                   @RequestParam("file") MultipartFile file) throws Exception {
        return ruleService.saveRule(name, type, file);
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("loadedRules", kieManager.getLoadedRuleNames().size());
        return Result.success(data);
    }

    /** Kept for compatibility with the original demo. */
    @GetMapping("/health/test")
    public String test() {
        return "TEST SUCCESS";
    }

    @GetMapping("/list")
    public Result<List<RuleMeta>> list() {
        return ruleService.listRules();
    }

    /**
     * Legacy demo execution. Converts {"amount": ...} into the built-in Order example.
     */
    @PostMapping("/exec/{name}")
    public Result<Object> exec(@PathVariable String name, @RequestBody Map<String, Object> fact) throws Exception {
        return ruleService.executeByName(name, fact);
    }

    /**
     * Generic execution endpoint. The incoming JSON object is inserted into Drools as a Map fact.
     */
    @PostMapping("/exec-map/{name}")
    public Result<Map<String, Object>> execMap(@PathVariable String name,
                                               @RequestBody Map<String, Object> fact) throws Exception {
        return ruleService.executeMapByName(name, fact);
    }

    @PostMapping("/validate")
    public Result<Void> validate(@RequestParam String name, @RequestBody String content) {
        return ruleService.validateRule(name, content);
    }

    @PostMapping("/refresh/{name}")
    public Result<Void> refresh(@PathVariable String name) {
        BuildResult result = kieManager.reloadRuleFromDatabase(name);
        return result.getStatus() == BuildResult.Status.SUCCESS
                ? Result.success()
                : Result.fail(result.getMessage());
    }

    @PostMapping("/reload-all")
    public Result<Void> reloadAllRules() {
        kieManager.loadAllRulesFromDatabase();
        return Result.success();
    }

    @GetMapping("/loaded")
    public Result<Set<String>> loadedRules() {
        return ruleService.getLoadedRuleNames();
    }

    @GetMapping("/rule-content/{name}")
    public Result<String> getRuleContent(@PathVariable String name) {
        Result<RuleMeta> result = ruleService.getRule(name);
        if (result.getData() == null) {
            return Result.fail(result.getMessage());
        }
        return Result.success(result.getData().getContent());
    }

    @PutMapping("/{name}")
    public Result<Void> updateRule(@PathVariable String name, @RequestBody String content) throws Exception {
        return ruleService.updateRuleContent(name, content);
    }

    @PatchMapping("/{name}/status")
    public Result<Void> setStatus(@PathVariable String name, @RequestParam String status) {
        return ruleService.setRuleStatus(name, status);
    }

    @DeleteMapping("/{name}")
    public Result<Void> delete(@PathVariable String name) {
        return ruleService.deleteRule(name);
    }

    @GetMapping("/meta/{name}")
    public Result<RuleMeta> meta(@PathVariable String name) {
        return ruleService.getRule(name);
    }
}
