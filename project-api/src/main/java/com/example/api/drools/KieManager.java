package com.example.api.drools;

import com.example.api.entity.RuleMeta;
import com.example.api.repository.RuleRepository;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds Drools containers from DRL text and keeps one active container per rule name.
 * A failed build never replaces the last successfully built container.
 */
@Component
public class KieManager {
    private static final Logger logger = LoggerFactory.getLogger(KieManager.class);

    private final KieServices kieServices = KieServices.Factory.get();
    private final Map<String, KieContainer> cache = new ConcurrentHashMap<>();
    private final AtomicLong buildSequence = new AtomicLong();

    @Autowired(required = false)
    private RuleRepository ruleRepository;

    public void setRuleRepository(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public BuildResult buildOrUpdateReport(String name, String drl) {
        return compile(name, drl, true);
    }

    /**
     * Compile a rule without changing the active cache. Useful for editor validation.
     */
    public BuildResult validateReport(String name, String drl) {
        return compile(name, drl, false);
    }

    private BuildResult compile(String name, String drl, boolean install) {
        if (name == null || name.trim().isEmpty()) {
            return failure("Rule name cannot be null or empty");
        }
        if (drl == null || drl.trim().isEmpty()) {
            return failure("DRL content cannot be null or empty");
        }

        String artifactId = sanitizeArtifactId(name);
        String version = "1.0." + System.currentTimeMillis() + "-" + buildSequence.incrementAndGet();
        ReleaseId releaseId = kieServices.newReleaseId("com.azurefly.rules", artifactId, version);
        KieContainer candidate = null;

        try {
            KieFileSystem fileSystem = kieServices.newKieFileSystem();
            fileSystem.generateAndWritePomXML(releaseId);

            String path = "src/main/resources/rules/" + artifactId + ".drl";
            Resource resource = kieServices.getResources()
                    .newByteArrayResource(drl.getBytes(StandardCharsets.UTF_8))
                    .setResourceType(ResourceType.DRL)
                    .setTargetPath(path);
            fileSystem.write(resource);

            KieBuilder builder = kieServices.newKieBuilder(fileSystem);
            builder.buildAll();

            if (builder.getResults().hasMessages(Message.Level.ERROR)) {
                return failure(joinMessages(builder.getResults().getMessages(Message.Level.ERROR)));
            }

            String warnings = builder.getResults().hasMessages(Message.Level.WARNING)
                    ? joinMessages(builder.getResults().getMessages(Message.Level.WARNING))
                    : null;

            candidate = kieServices.newKieContainer(releaseId);
            if (install) {
                KieContainer previous = cache.put(name, candidate);
                candidate = null; // ownership moved to cache
                disposeQuietly(previous);
                logger.info("Rule [{}] compiled and activated", name);
            }

            String message = warnings == null || warnings.trim().isEmpty()
                    ? (install ? "built and activated" : "validation passed")
                    : (install ? "built and activated with warnings: " : "validation passed with warnings: ") + warnings;
            return new BuildResult(BuildResult.Status.SUCCESS, message);
        } catch (Exception ex) {
            logger.error("Failed to compile rule [{}]", name, ex);
            return failure(exceptionMessage(ex));
        } finally {
            disposeQuietly(candidate);
        }
    }

    public void buildOrUpdate(String name, String drl) {
        BuildResult result = buildOrUpdateReport(name, drl);
        if (result.getStatus() != BuildResult.Status.SUCCESS) {
            throw new IllegalArgumentException("Failed to build rule: " + result.getMessage());
        }
    }

    public void fireRules(String name, Object fact) {
        fireRulesAndCount(name, fact);
    }

    public int fireRulesAndCount(String name, Object fact) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Rule name cannot be null or empty");
        }
        if (fact == null) {
            throw new IllegalArgumentException("Fact cannot be null");
        }

        KieContainer container = cache.get(name);
        if (container == null) {
            throw new IllegalStateException("KieContainer not found for: " + name + ". Build or reload the rule first.");
        }

        KieSession session = null;
        try {
            session = container.newKieSession();
            session.insert(fact);
            return session.fireAllRules();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fire rule [" + name + "]: " + ex.getMessage(), ex);
        } finally {
            if (session != null) {
                session.dispose();
            }
        }
    }

    public boolean hasContainer(String name) {
        return name != null && cache.containsKey(name);
    }

    public Set<String> getLoadedRuleNames() {
        return Collections.unmodifiableSet(new HashSet<>(cache.keySet()));
    }

    public void removeContainer(String name) {
        if (name == null) {
            return;
        }
        disposeQuietly(cache.remove(name));
    }

    public void clearContainers() {
        for (KieContainer container : cache.values()) {
            disposeQuietly(container);
        }
        cache.clear();
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

    private String sanitizeArtifactId(String name) {
        String sanitized = name.trim().replaceAll("[^A-Za-z0-9_.-]", "-");
        return sanitized.isEmpty() ? "rule" : sanitized;
    }

    private String joinMessages(List<Message> messages) {
        StringBuilder builder = new StringBuilder();
        for (Message message : messages) {
            if (message == null || message.getText() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(message.getText());
        }
        return builder.length() == 0 ? "Drools build failed with no diagnostic message" : builder.toString();
    }

    private BuildResult failure(String message) {
        return new BuildResult(BuildResult.Status.FAILURE, message);
    }

    private String exceptionMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = ex.getClass().getSimpleName();
        }
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            message += " (caused by: " + ex.getCause().getMessage() + ")";
        }
        return message;
    }

    private void disposeQuietly(KieContainer container) {
        if (container == null) {
            return;
        }
        try {
            container.dispose();
        } catch (Exception ex) {
            logger.debug("Failed to dispose KieContainer cleanly", ex);
        }
    }
}
