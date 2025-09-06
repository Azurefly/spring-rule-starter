package com.example.api.drools;

import org.kie.api.KieServices;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.api.entity.RuleMeta;
import com.example.api.repository.RuleRepository;
import com.example.api.drools.BuildResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * KieManager: builds KieContainer from DRL text and caches containers by rule name.
 * Supports rebuilding (hot-reload) by creating a new ReleaseId for each build.
 * Compatible with Drools 7.x and Java 8.
 */
@Component
public class KieManager {
    private static final Logger logger = LoggerFactory.getLogger(KieManager.class);
    private final KieServices ks = KieServices.Factory.get();
    private final Map<String, KieContainer> cache = new ConcurrentHashMap<>();
    
    @Autowired(required = false)
    private RuleRepository ruleRepository;
    
    // 添加一个方法来手动设置RuleRepository（用于测试）
    public void setRuleRepository(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /**
     * Build or update a KieContainer for a rule (identified by name).
     * Returns BuildResult indicating success/failure and message.
     */
    public BuildResult buildOrUpdateReport(String name, String drl) {
        try {
            logger.info("=== Building rule: {} ===", name);
            logger.info("DRL content length: {}", (drl != null ? drl.length() : "null"));
            
            if (name == null || name.trim().isEmpty()) {
                logger.error("Rule name is null or empty");
                return new BuildResult(BuildResult.Status.FAILURE, "Rule name cannot be null or empty");
            }
            if (drl == null || drl.trim().isEmpty()) {
                logger.error("DRL content is null or empty");
                return new BuildResult(BuildResult.Status.FAILURE, "DRL content cannot be null or empty");
            }
            
            logger.debug("Creating release ID...");
            String version = String.valueOf(System.currentTimeMillis());
            ReleaseId releaseId = ks.newReleaseId("com.example.rules", name, version);
            logger.debug("Release ID: {}", releaseId);
            
            logger.debug("Creating KieFileSystem...");
            KieFileSystem kfs = ks.newKieFileSystem();
            kfs.generateAndWritePomXML(releaseId);
            
            logger.debug("Adding DRL resource...");
            String path = "src/main/resources/rules/" + name + ".drl";
            Resource r = ks.getResources().newByteArrayResource(drl.getBytes(StandardCharsets.UTF_8))
                    .setResourceType(ResourceType.DRL)
                    .setTargetPath(path);
            kfs.write(r);
            logger.debug("DRL resource written to path: {}", path);
            
            logger.debug("Building KieBuilder...");
            KieBuilder kb = ks.newKieBuilder(kfs);
            kb.buildAll();
            logger.debug("Build completed");
            
            if (kb.getResults().hasMessages(Message.Level.ERROR)) {
                StringBuilder sb = new StringBuilder();
                for (Message m : kb.getResults().getMessages(Message.Level.ERROR)) {
                    if (m != null && m.getText() != null) {
                        sb.append(m.getText()).append("\n");
                    }
                }
                String errorMessage = sb.toString().trim();
                if (errorMessage.isEmpty()) {
                    errorMessage = "Build failed with unspecified errors";
                }
                logger.error("Drools build errors for {}: {}", name, errorMessage);
                return new BuildResult(BuildResult.Status.FAILURE, errorMessage);
            }
            if (kb.getResults().hasMessages(Message.Level.WARNING)) {
                StringBuilder sb = new StringBuilder();
                for (Message m : kb.getResults().getMessages(Message.Level.WARNING)) {
                    sb.append(m.getText()).append("\n");
                }
                logger.warn("Drools build warnings for {}: {}", name, sb.toString());
            }
            
            logger.debug("Creating KieContainer...");
            KieContainer kc = ks.newKieContainer(releaseId);
            cache.put(name, kc);
            logger.info("Successfully built rule: {}", name);
            return new BuildResult(BuildResult.Status.SUCCESS, "built");
        } catch (Exception e) {
            logger.error("Exception building rule {}: {}", name, e.getMessage(), e);
            
            String errorMessage = "Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            if (e.getCause() != null) {
                errorMessage += " (Caused by: " + e.getCause().getClass().getSimpleName() + " - " + e.getCause().getMessage() + ")";
            }
            
            return new BuildResult(BuildResult.Status.FAILURE, errorMessage);
        }
    }

    /**
     * Legacy method that throws on failure.
     */
    public void buildOrUpdate(String name, String drl) {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Rule name cannot be null or empty");
        }
        if (drl == null || drl.trim().isEmpty()) {
            throw new RuntimeException("DRL content cannot be null or empty");
        }
        BuildResult res = buildOrUpdateReport(name, drl);
        if (res.getStatus() != BuildResult.Status.SUCCESS) {
            throw new RuntimeException("Failed to build rule: " + res.getMessage());
        }
    }

    /**
     * Fire rules for the named container. If not present in cache, RuntimeException.
     * Uses a fresh KieSession (stateful) for each invocation.
     */
    public void fireRules(String name, Object fact) {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Rule name cannot be null or empty");
        }
        if (fact == null) {
            throw new RuntimeException("Fact cannot be null");
        }
        KieContainer kc = cache.get(name);
        if (kc == null) {
            throw new RuntimeException("KieContainer not found for: " + name + ". Call buildOrUpdate first.");
        }
        KieSession session = null;
        try {
            session = kc.newKieSession();
            session.insert(fact);
            session.fireAllRules();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fire rules: " + ex.getMessage(), ex);
        } finally {
            if (session != null) {
                session.dispose();
            }
        }
    }

    /**
     * Check if a container exists
     */
    public boolean hasContainer(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return cache.containsKey(name);
    }
    
    /**
     * Load rule from database by name and build container
     */
    public BuildResult loadRuleFromDatabase(String name) {
        if (ruleRepository == null) {
            return new BuildResult(BuildResult.Status.FAILURE, "RuleRepository not available");
        }
        
        Optional<RuleMeta> ruleOpt = ruleRepository.findByName(name);
        if (!ruleOpt.isPresent()) {
            return new BuildResult(BuildResult.Status.FAILURE, "Rule not found in database: " + name);
        }
        
        RuleMeta rule = ruleOpt.get();
        if (!"DROOLS".equalsIgnoreCase(rule.getType())) {
            return new BuildResult(BuildResult.Status.FAILURE, "Unsupported rule type: " + rule.getType());
        }
        
        if (rule.getContent() == null || rule.getContent().trim().isEmpty()) {
            return new BuildResult(BuildResult.Status.FAILURE, "Rule content is empty");
        }
        
        return buildOrUpdateReport(name, rule.getContent());
    }
    
    /**
     * Load all enabled DROOLS rules from database
     */
    public void loadAllRulesFromDatabase() {
        if (ruleRepository == null) {
            logger.warn("RuleRepository not available, skipping rule loading");
            return;
        }
        
        try {
            List<RuleMeta> rules = ruleRepository.findAll();
            int loaded = 0;
            int failed = 0;
            
            for (RuleMeta rule : rules) {
                if ("ENABLED".equals(rule.getStatus()) && "DROOLS".equalsIgnoreCase(rule.getType())) {
                    BuildResult result = loadRuleFromDatabase(rule.getName());
                    if (result.getStatus() == BuildResult.Status.SUCCESS) {
                        loaded++;
                        logger.info("Successfully loaded rule: {}", rule.getName());
                    } else {
                        failed++;
                        logger.error("Failed to load rule {}: {}", rule.getName(), result.getMessage());
                    }
                }
            }
            
            logger.info("Rule loading completed. Loaded: {}, Failed: {}", loaded, failed);
        } catch (Exception e) {
            logger.error("Error loading rules from database: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Reload a specific rule from database
     */
    public BuildResult reloadRuleFromDatabase(String name) {
        System.out.println("=== KieManager.reloadRuleFromDatabase called for: " + name + " ===");
        logger.error("=== KieManager.reloadRuleFromDatabase called for: {} ===", name);
        logger.info("=== Reloading rule from database: {} ===", name);
        
        try {
            // Remove from cache first
            cache.remove(name);
            
            BuildResult result = loadRuleFromDatabase(name);
            System.out.println("LoadRuleFromDatabase result: " + result.getStatus() + " - " + result.getMessage());
            
            // Update the database with build status
            if (ruleRepository != null) {
                try {
                    Optional<RuleMeta> ruleOpt = ruleRepository.findByName(name);
                    if (ruleOpt.isPresent()) {
                        RuleMeta rule = ruleOpt.get();
                        rule.setLastBuildAt(java.time.LocalDateTime.now());
                        rule.setLastBuildStatus(result.getStatus().name());
                        rule.setLastBuildMessage(result.getMessage());
                        ruleRepository.save(rule);
                        logger.info("Updated rule {} build status to: {}", name, result.getStatus());
                        System.out.println("Database updated successfully for rule: " + name);
                    } else {
                        System.out.println("Rule not found in database: " + name);
                    }
                } catch (Exception e) {
                    logger.error("Failed to update rule build status in database: {}", e.getMessage(), e);
                    System.out.println("Database update failed: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("RuleRepository is null!");
            }
            
            return result;
        } catch (Exception e) {
            System.out.println("Exception in reloadRuleFromDatabase: " + e.getMessage());
            e.printStackTrace();
            return new BuildResult(BuildResult.Status.FAILURE, "Exception: " + e.getMessage());
        }
    }
    
    /**
     * Get rule content from database
     */
    public String getRuleContentFromDatabase(String name) {
        if (ruleRepository == null) {
            return null;
        }
        
        Optional<RuleMeta> ruleOpt = ruleRepository.findByName(name);
        return ruleOpt.map(RuleMeta::getContent).orElse(null);
    }
}
