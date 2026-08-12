package com.example.api.drools;

import com.azurefly.rule.core.DroolsRuleEngine;
import com.azurefly.rule.core.RuleBuildResult;
import com.azurefly.rule.core.RuleEngine;
import com.example.api.entity.RuleMeta;
import com.example.api.repository.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Admin-side adapter around the reusable {@link RuleEngine}.
 *
 * <p>This class owns database loading/status semantics only. Dynamic Drools
 * compilation, container lifecycle and execution live in spring-rule-core so
 * starter consumers and the bundled admin server use the same runtime.</p>
 */
@Component
public class KieManager {
    private static final Logger logger = LoggerFactory.getLogger(KieManager.class);

    private final RuleEngine ruleEngine;

    @Autowired(required = false)
    private RuleRepository ruleRepository;

    /**
     * Compatibility constructor used by existing unit tests and direct callers.
     */
    public KieManager() {
        this(new DroolsRuleEngine());
    }

    @Autowired
    public KieManager(RuleEngine ruleEngine) {
        if (ruleEngine == null) {
            throw new IllegalArgumentException("RuleEngine cannot be null");
        }
        this.ruleEngine = ruleEngine;
    }

    public void setRuleRepository(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public BuildResult buildOrUpdateReport(String name, String drl) {
        return adapt(ruleEngine.install(name, drl));
    }

    public BuildResult validateReport(String name, String drl) {
        return adapt(ruleEngine.validate(name, drl));
    }

    public void buildOrUpdate(String name, String drl) {
        BuildResult result = buildOrUpdateReport(name, drl);
        if (result.getStatus() != BuildResult.Status.SUCCESS) {
            throw new IllegalArgumentException("Failed to build rule: " + result.getMessage());
        }
    }

    public void fireRules(String name, Object fact) {
        ruleEngine.execute(name, fact);
    }

    public int fireRulesAndCount(String name, Object fact) {
        return ruleEngine.execute(name, fact);
    }

    public boolean hasContainer(String name) {
        return ruleEngine.contains(name);
    }

    public Set<String> getLoadedRuleNames() {
        return ruleEngine.getLoadedRuleNames();
    }

    public void removeContainer(String name) {
        ruleEngine.remove(name);
    }

    public void clearContainers() {
        ruleEngine.clear();
    }

    public BuildResult loadRuleFromDatabase(String name) {
        if (ruleRepository == null) {
            return failure("RuleRepository not available");
        }

        Optional<RuleMeta> ruleOptional = ruleRepository.findByName(name);
        if (!ruleOptional.isPresent()) {
            removeContainer(name);
            return failure("Rule not found in database: " + name);
        }

        RuleMeta rule = ruleOptional.get();
        if (!"ENABLED".equalsIgnoreCase(rule.getStatus())) {
            removeContainer(name);
            return failure("Rule is disabled: " + name);
        }
        if (!"DROOLS".equalsIgnoreCase(rule.getType())) {
            removeContainer(name);
            return failure("Unsupported rule type: " + rule.getType());
        }
        return buildOrUpdateReport(name, rule.getContent());
    }

    /**
     * Rebuild the in-memory state from the database so disabled/deleted rules cannot remain active.
     */
    public void loadAllRulesFromDatabase() {
        if (ruleRepository == null) {
            logger.warn("RuleRepository not available; database rule loading is skipped");
            return;
        }

        clearContainers();
        List<RuleMeta> rules = ruleRepository.findAll();
        int loaded = 0;
        int failed = 0;
        for (RuleMeta rule : rules) {
            if ("ENABLED".equalsIgnoreCase(rule.getStatus()) && "DROOLS".equalsIgnoreCase(rule.getType())) {
                BuildResult result = buildOrUpdateReport(rule.getName(), rule.getContent());
                if (result.getStatus() == BuildResult.Status.SUCCESS) {
                    loaded++;
                } else {
                    failed++;
                    logger.error("Failed to load rule [{}]: {}", rule.getName(), result.getMessage());
                }
            }
        }
        logger.info("Database rule loading completed: loaded={}, failed={}", loaded, failed);
    }

    public BuildResult reloadRuleFromDatabase(String name) {
        if (ruleRepository == null) {
            return failure("RuleRepository not available");
        }

        Optional<RuleMeta> ruleOptional = ruleRepository.findByName(name);
        if (!ruleOptional.isPresent()) {
            removeContainer(name);
            return failure("Rule not found in database: " + name);
        }

        RuleMeta rule = ruleOptional.get();
        if (!"ENABLED".equalsIgnoreCase(rule.getStatus())) {
            removeContainer(name);
            return new BuildResult(BuildResult.Status.SUCCESS, "rule disabled; active container removed");
        }

        BuildResult result = buildOrUpdateReport(rule.getName(), rule.getContent());
        rule.setLastBuildAt(LocalDateTime.now());
        rule.setLastBuildStatus(result.getStatus().name());
        rule.setLastBuildMessage(result.getMessage());
        ruleRepository.save(rule);
        return result;
    }

    public String getRuleContentFromDatabase(String name) {
        if (ruleRepository == null) {
            return null;
        }
        return ruleRepository.findByName(name).map(RuleMeta::getContent).orElse(null);
    }

    private BuildResult adapt(RuleBuildResult result) {
        BuildResult.Status status = result.isSuccess() ? BuildResult.Status.SUCCESS : BuildResult.Status.FAILURE;
        return new BuildResult(status, result.getMessage());
    }

    private BuildResult failure(String message) {
        return new BuildResult(BuildResult.Status.FAILURE, message);
    }
}
