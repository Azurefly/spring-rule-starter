package com.example.api.service;

import com.example.api.entity.RuleBuildHistory;
import com.example.api.entity.RuleMeta;
import com.example.common.Result;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RuleService {
    Result<Void> saveRule(String name, String type, MultipartFile contentFile) throws Exception;
    Result<List<RuleMeta>> listRules();
    Result<Object> executeByName(String name, Map<String, Object> fact) throws Exception;
    Result<Map<String, Object>> executeMapByName(String name, Map<String, Object> fact) throws Exception;
    Result<Void> updateRuleContent(String name, String content) throws Exception;
    Result<Void> validateRule(String name, String content);
    Result<Void> setRuleStatus(String name, String status);
    Result<Void> deleteRule(String name);
    Result<RuleMeta> getRule(String name);
    Result<List<RuleBuildHistory>> getBuildHistory(String name);
    Result<Map<String, Object>> getBuildHistoryPage(String name, int page, int size);
    Result<Void> rollbackRuleToVersion(String name, Integer version) throws Exception;
    Result<Set<String>> getLoadedRuleNames();
}
