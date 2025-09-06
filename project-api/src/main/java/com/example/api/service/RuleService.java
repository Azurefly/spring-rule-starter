
package com.example.api.service;

import com.example.common.Result;
import com.example.api.entity.RuleMeta;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface RuleService {
    Result<Void> saveRule(String name, String type, MultipartFile contentFile) throws Exception;
    Result<List<RuleMeta>> listRules();
    Result<Object> executeByName(String name, Map<String,Object> fact) throws Exception;
    Result<Void> updateRuleContent(String name, String content) throws Exception;
    Result<com.example.api.entity.RuleMeta> getRule(String name);
    Result<java.util.List<com.example.api.entity.RuleBuildHistory>> getBuildHistory(String name);
    Result<java.util.Map<String,Object>> getBuildHistoryPage(String name, int page, int size);
    Result<Void> rollbackRuleToVersion(String name, Integer version) throws Exception;

}
