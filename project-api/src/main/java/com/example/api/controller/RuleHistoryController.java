
package com.example.api.controller;

import com.example.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuleHistoryController {

    @Autowired
    private com.example.api.service.RuleService ruleService;

    @PostMapping("/api/rules/rollback/{name}/{version}")
    public Result<Void> rollback(@PathVariable String name, @PathVariable Integer version) throws Exception {
        try {
            return ruleService.rollbackRuleToVersion(name, version);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/api/rules/history/{name}")
    public Result<java.util.Map<String,Object>> history(@PathVariable String name,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        try {
            return ruleService.getBuildHistoryPage(name, page, size);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
