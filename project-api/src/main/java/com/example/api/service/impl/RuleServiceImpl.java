
package com.example.api.service.impl;

import com.example.api.entity.RuleMeta;
import com.example.api.entity.RuleBuildHistory;
import com.example.api.repository.RuleRepository;
import com.example.api.repository.RuleBuildHistoryRepository;
import com.example.api.service.RuleService;
import com.example.common.Result;
import com.example.ruleengine.Order;
import com.example.api.drools.KieManager;
import com.example.ruleengine.drools.DroolsHelper;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.LocalDateTime;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

@Service
@Transactional
public class RuleServiceImpl implements RuleService {
    private static final Logger logger = LoggerFactory.getLogger(RuleServiceImpl.class);

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired(required = false)
    private RuleBuildHistoryRepository historyRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private DroolsHelper droolsHelper;

    @Autowired
    private KieManager kieManager;

    @Override
    public Result<Void> saveRule(String name, String type, MultipartFile contentFile) throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Rule name cannot be null or empty");
        }
        if (type == null || type.trim().isEmpty()) {
            throw new RuntimeException("Rule type cannot be null or empty");
        }
        if (contentFile == null || contentFile.isEmpty()) {
            throw new RuntimeException("Content file cannot be null or empty");
        }
        
        Optional<RuleMeta> exists = ruleRepository.findByName(name);
        if (exists.isPresent()) {
            throw new RuntimeException("rule exists: " + name);
        }
        
        String content;
        try (InputStream in = contentFile.getInputStream(); Scanner sc = new Scanner(in, StandardCharsets.UTF_8.name())) {
            sc.useDelimiter("\\A");
            content = sc.hasNext() ? sc.next() : "";
        }
        
        RuleMeta m = new RuleMeta();
        m.setName(name);
        m.setType(type);
        m.setContent(content);
        m.setStatus("ENABLED");
        m.setVersion(1);
        ruleRepository.save(m);
        
        // Persist DRL in DB only and trigger build (no write-back to source tree)
        try {
            logger.info("=== RuleServiceImpl.saveRule ===");
            logger.info("Calling kieManager.buildOrUpdateReport for: {}", m.getName());
            logger.info("Content length: {}", (m.getContent() != null ? m.getContent().length() : "null"));
            logger.debug("KieManager instance: {}", (kieManager != null ? "NOT NULL" : "NULL"));
            
            if (kieManager == null) {
                throw new RuntimeException("KieManager is null - dependency injection failed");
            }
            
            com.example.api.drools.BuildResult res = kieManager.buildOrUpdateReport(m.getName(), m.getContent());
            logger.info("Build result status: {}", res.getStatus());
            logger.info("Build result message: {}", res.getMessage());
            
            m.setLastBuildAt(LocalDateTime.now());
            m.setLastBuildStatus(res.getStatus().name());
            m.setLastBuildMessage(res.getMessage());
            ruleRepository.save(m);
            
            // 如果构建失败，记录错误信息
            if (res.getStatus() == com.example.api.drools.BuildResult.Status.FAILURE) {
                logger.error("Rule build failed for {}: {}", m.getName(), res.getMessage());
            }
            
            // record build history
            if (historyRepository != null) {
                try {
                    RuleBuildHistory h = new RuleBuildHistory();
                    h.setRuleName(m.getName()); 
                    h.setVersion(m.getVersion()); 
                    h.setStatus(res.getStatus().name()); 
                    h.setMessage(res.getMessage()); 
                    h.setBuiltBy("system");
                    historyRepository.save(h);
                } catch (Exception hx) { 
                    hx.printStackTrace(); 
                }
            }
            // publish redis refresh for cluster nodes
            if (redisTemplate != null) redisTemplate.convertAndSend("rule-refresh", m.getName());
        } catch (Exception ex) {
            // log and continue; building the container is best-effort here
            try {
                java.io.FileWriter writer = new java.io.FileWriter("/tmp/rule_service_debug.log", true);
                writer.write("=== Exception in RuleServiceImpl.saveRule ===\n");
                writer.write("Exception type: " + ex.getClass().getSimpleName() + "\n");
                writer.write("Exception message: " + ex.getMessage() + "\n");
                writer.write("Stack trace: " + java.util.Arrays.toString(ex.getStackTrace()) + "\n");
                writer.close();
            } catch (Exception logEx) {
                // ignore
            }
            
            System.err.println("=== Exception in RuleServiceImpl.saveRule ===");
            System.err.println("Exception type: " + ex.getClass().getSimpleName());
            System.err.println("Exception message: " + ex.getMessage());
            ex.printStackTrace();
            
            // Update the rule with build failure status
            m.setLastBuildAt(LocalDateTime.now());
            m.setLastBuildStatus("FAILURE");
            String errorMessage = ex.getMessage() != null ? ex.getMessage() : "Unknown error occurred: " + ex.getClass().getSimpleName();
            if (ex.getCause() != null) {
                errorMessage += " (Caused by: " + ex.getCause().getClass().getSimpleName() + " - " + ex.getCause().getMessage() + ")";
            }
            m.setLastBuildMessage(errorMessage);
            try {
                ruleRepository.save(m);
                System.err.println("Rule saved with FAILURE status");
            } catch (Exception saveEx) {
                System.err.println("Failed to save rule: " + saveEx.getMessage());
                saveEx.printStackTrace();
            }
        }
        return Result.success();
    }

    @Override
    public Result<List<RuleMeta>> listRules() {
        try {
            return Result.success(ruleRepository.findAll());
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @Override
    public Result<Object> executeByName(String name, Map<String, Object> fact) throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Rule name cannot be null or empty");
        }
        if (fact == null) {
            throw new RuntimeException("Fact cannot be null");
        }
        RuleMeta meta = ruleRepository.findByName(name).orElseThrow(() -> new RuntimeException("rule not found: " + name));
        if (!"DROOLS".equalsIgnoreCase(meta.getType())) {
            throw new RuntimeException("unsupported rule type: " + meta.getType());
        }
        // For demo: we expect a JSON with 'amount' to map to Order
        Object amountObj = fact.get("amount");
        double amount = 0.0;
        if (amountObj instanceof Number) amount = ((Number) amountObj).doubleValue();
        else if (amountObj != null) amount = Double.parseDouble(amountObj.toString());
        Order o = new Order(amount);

        // Use KieManager to fire rules for the specific rule name
        // First ensure the container is built, try loading from database first
        if (!kieManager.hasContainer(name)) {
            System.out.println("=== DEBUG: Container not found for " + name + ", attempting to load from database ===");
            // Try to load from database first
            com.example.api.drools.BuildResult res = kieManager.loadRuleFromDatabase(name);
            System.out.println("=== DEBUG: Load from database result: " + res.getStatus() + " - " + res.getMessage() + " ===");
            if (res.getStatus() != com.example.api.drools.BuildResult.Status.SUCCESS) {
                System.out.println("=== DEBUG: Database load failed, trying to build from content ===");
                // Fallback to building from stored content
                res = kieManager.buildOrUpdateReport(name, meta.getContent());
                System.out.println("=== DEBUG: Build from content result: " + res.getStatus() + " - " + res.getMessage() + " ===");
                if (res.getStatus() != com.example.api.drools.BuildResult.Status.SUCCESS) {
                    throw new RuntimeException("Failed to build rule container: " + res.getMessage());
                }
            }
        } else {
            System.out.println("=== DEBUG: Container found for " + name + " ===");
        }
        kieManager.fireRules(name, o);
        return Result.success(o);
    }
    // rebuild container explicitly (used by controller refresh)
    @Override
    public Result<Void> updateRuleContent(String name, String content) throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Rule name cannot be null or empty");
        }
        if (content == null) {
            throw new RuntimeException("Content cannot be null");
        }
        RuleMeta meta = ruleRepository.findByName(name).orElseThrow(() -> new RuntimeException("rule not found: " + name));
        meta.setContent(content);
        meta.setVersion(meta.getVersion() == null ? 1 : meta.getVersion() + 1);
        ruleRepository.save(meta);
        // attempt rebuild
        try {
            com.example.api.drools.BuildResult res = kieManager.buildOrUpdateReport(meta.getName(), meta.getContent());
            meta.setLastBuildAt(LocalDateTime.now());
            meta.setLastBuildStatus(res.getStatus().name());
            meta.setLastBuildMessage(res.getMessage());
            ruleRepository.save(meta);
            
            // 如果构建失败，记录错误信息
            if (res.getStatus() == com.example.api.drools.BuildResult.Status.FAILURE) {
                System.err.println("Rule build failed for " + meta.getName() + ": " + res.getMessage());
            }
            
            if (redisTemplate != null) redisTemplate.convertAndSend("rule-refresh", meta.getName());
        } catch (Exception ex) {
            // Update the rule with build failure status
            meta.setLastBuildAt(LocalDateTime.now());
            meta.setLastBuildStatus("FAILURE");
            meta.setLastBuildMessage(ex.getMessage());
            ruleRepository.save(meta);
            throw ex; // Re-throw to be handled by the caller
        }
        return Result.success();
    }

    @Override
    public Result<com.example.api.entity.RuleMeta> getRule(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Result.fail("Rule name cannot be null or empty");
        }
        return Result.success(ruleRepository.findByName(name).orElse(null));
    }

    // This method is called via reflection from RuleController.refresh()
    @SuppressWarnings("unused")
    private void rebuildContainerForRule(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Rule name cannot be null or empty");
        }
        RuleMeta meta = ruleRepository.findByName(name).orElseThrow(() -> new RuntimeException("rule not found: " + name));
        try {
            kieManager.buildOrUpdate(meta.getName(), meta.getContent());
        } catch (Exception ex) {
            // Update the rule with build failure status
            meta.setLastBuildAt(LocalDateTime.now());
            meta.setLastBuildStatus("FAILURE");
            meta.setLastBuildMessage(ex.getMessage());
            ruleRepository.save(meta);
            throw ex; // Re-throw to be handled by the caller
        }
    }

    // expose for controller access
    public RuleMeta getRuleByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        return ruleRepository.findByName(name).orElse(null);
    }

    @Override
    public Result<java.util.Map<String,Object>> getBuildHistoryPage(String name, int page, int size) {
        try {
            if (name == null || name.trim().isEmpty()) {
                return Result.fail("Rule name cannot be null or empty");
            }
            if (historyRepository == null) {
                return Result.fail("history repository is not available");
            }
            org.springframework.data.domain.Pageable pg = org.springframework.data.domain.PageRequest.of(page < 0 ? 0 : page, size <= 0 ? 10 : size);
            org.springframework.data.domain.Page<com.example.api.entity.RuleBuildHistory> p = historyRepository.findByRuleNameOrderByBuiltAtDesc(name, pg);
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("items", p.getContent());
            m.put("total", p.getTotalElements());
            m.put("page", p.getNumber());
            m.put("size", p.getSize());
            return Result.success(m);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @Override
    public Result<java.util.List<com.example.api.entity.RuleBuildHistory>> getBuildHistory(String name) {
        try {
            if (name == null || name.trim().isEmpty()) {
                return Result.fail("Rule name cannot be null or empty");
            }
            if (historyRepository == null) {
                return Result.fail("history repository is not available");
            }
            return Result.success(historyRepository.findByRuleNameOrderByBuiltAtDesc(name));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @Override
    public Result<Void> rollbackRuleToVersion(String name, Integer version) throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Rule name cannot be null or empty");
        }
        if (version == null) {
            throw new RuntimeException("Version cannot be null");
        }
        RuleMeta meta = ruleRepository.findByName(name).orElseThrow(() -> new RuntimeException("rule not found: " + name));
        // find the history entry with requested version (latest by builtAt)
        if (historyRepository == null) {
            throw new RuntimeException("history repository is not available");
        }
        java.util.List<com.example.api.entity.RuleBuildHistory> list = historyRepository.findByRuleNameOrderByBuiltAtDesc(name);
        com.example.api.entity.RuleBuildHistory target = null;
        for (com.example.api.entity.RuleBuildHistory h : list) {
            if (h.getVersion() != null && h.getVersion().intValue() == version.intValue()) { target = h; break; }
        }
        if (target == null) throw new RuntimeException("history for version not found: " + version);
        // apply content from history as new version
        meta.setContent(target.getContent());
        meta.setVersion(meta.getVersion() == null ? 1 : meta.getVersion() + 1);
        ruleRepository.save(meta);
        // attempt rebuild
        try {
            com.example.api.drools.BuildResult res = kieManager.buildOrUpdateReport(meta.getName(), meta.getContent());
            meta.setLastBuildAt(LocalDateTime.now());
            meta.setLastBuildStatus(res.getStatus().name());
            meta.setLastBuildMessage(res.getMessage());
            ruleRepository.save(meta);
            try {
                com.example.api.entity.RuleBuildHistory h2 = new com.example.api.entity.RuleBuildHistory();
                h2.setRuleName(meta.getName()); h2.setVersion(meta.getVersion()); h2.setStatus(res.getStatus().name()); h2.setMessage(res.getMessage()); h2.setContent(meta.getContent()); h2.setBuiltBy("rollback");
                historyRepository.save(h2);
            } catch (Exception hx) { hx.printStackTrace(); }
            if (redisTemplate != null) redisTemplate.convertAndSend("rule-refresh", meta.getName());
        } catch (Exception ex) {
            // Update the rule with build failure status
            meta.setLastBuildAt(LocalDateTime.now());
            meta.setLastBuildStatus("FAILURE");
            meta.setLastBuildMessage(ex.getMessage());
            ruleRepository.save(meta);
            throw ex; // Re-throw to be handled by the caller
        }
        return Result.success();
}

}