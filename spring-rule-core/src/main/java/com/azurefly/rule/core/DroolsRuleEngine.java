package com.azurefly.rule.core;

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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe dynamic Drools runtime.
 *
 * <p>A candidate rule is fully compiled before it replaces the active container.
 * Execution holds a read lock while a session is using the container, so an
 * install/remove operation cannot dispose that container mid-flight.</p>
 */
public class DroolsRuleEngine implements RuleEngine {
    private static final Logger logger = LoggerFactory.getLogger(DroolsRuleEngine.class);
    private static final String WARNING_PREFIX = "compiled and activated with warnings: ";

    public static final String DEFAULT_RELEASE_GROUP_ID = "com.azurefly.rules";
    public static final String DEFAULT_VERSION_PREFIX = "1.0";

    private final KieServices kieServices = KieServices.Factory.get();
    private final Map<String, KieContainer> cache = new ConcurrentHashMap<>();
    private final AtomicLong buildSequence = new AtomicLong();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final String releaseGroupId;
    private final String versionPrefix;
    private final List<RuleEngineListener> listeners;

    public DroolsRuleEngine() {
        this(DEFAULT_RELEASE_GROUP_ID, DEFAULT_VERSION_PREFIX, Collections.<RuleEngineListener>emptyList());
    }

    public DroolsRuleEngine(String releaseGroupId, String versionPrefix) {
        this(releaseGroupId, versionPrefix, Collections.<RuleEngineListener>emptyList());
    }

    public DroolsRuleEngine(String releaseGroupId,
                            String versionPrefix,
                            Iterable<RuleEngineListener> listeners) {
        this.releaseGroupId = requireText(releaseGroupId, "releaseGroupId");
        this.versionPrefix = requireText(versionPrefix, "versionPrefix");
        List<RuleEngineListener> copy = new ArrayList<>();
        if (listeners != null) {
            for (RuleEngineListener listener : listeners) {
                if (listener != null) {
                    copy.add(listener);
                }
            }
        }
        this.listeners = Collections.unmodifiableList(copy);
    }

    @Override
    public RuleBuildResult validate(String ruleName, String drl) {
        long started = System.nanoTime();
        CompiledRule candidate = compile(ruleName, drl);
        RuleBuildResult result = candidate.result;
        if (result.isSuccess()) {
            String buildMessage = result.getMessage();
            disposeQuietly(candidate.container);
            if (buildMessage != null && buildMessage.startsWith(WARNING_PREFIX)) {
                result = RuleBuildResult.success("validation passed with warnings: "
                        + buildMessage.substring(WARNING_PREFIX.length()));
            } else {
                result = RuleBuildResult.success("validation passed");
            }
        }
        publish(new RuleEngineEvent(
                RuleEngineEvent.Operation.VALIDATE,
                ruleName,
                result.isSuccess(),
                System.nanoTime() - started,
                0,
                result.getMessage()));
        return result;
    }

    @Override
    public RuleBuildResult install(String ruleName, String drl) {
        long started = System.nanoTime();
        CompiledRule candidate = compile(ruleName, drl);
        if (!candidate.result.isSuccess()) {
            publish(new RuleEngineEvent(
                    RuleEngineEvent.Operation.INSTALL,
                    ruleName,
                    false,
                    System.nanoTime() - started,
                    0,
                    candidate.result.getMessage()));
            return candidate.result;
        }

        lifecycleLock.writeLock().lock();
        try {
            KieContainer previous = cache.put(ruleName, candidate.container);
            disposeQuietly(previous);
            logger.info("Rule [{}] compiled and activated", ruleName);
        } finally {
            lifecycleLock.writeLock().unlock();
        }

        publish(new RuleEngineEvent(
                RuleEngineEvent.Operation.INSTALL,
                ruleName,
                true,
                System.nanoTime() - started,
                0,
                candidate.result.getMessage()));
        return candidate.result;
    }

    @Override
    public int execute(String ruleName, Object fact) {
        if (fact == null) {
            IllegalArgumentException failure = new IllegalArgumentException("Fact cannot be null");
            publish(new RuleEngineEvent(
                    RuleEngineEvent.Operation.EXECUTE,
                    ruleName,
                    false,
                    0L,
                    0,
                    failure.getMessage()));
            throw failure;
        }
        return execute(ruleName, Collections.singletonList(fact), Collections.<String, Object>emptyMap());
    }

    @Override
    public int execute(String ruleName, Iterable<?> facts, Map<String, Object> globals) {
        long started = System.nanoTime();
        String normalizedName;
        try {
            normalizedName = requireText(ruleName, "ruleName");
            if (facts == null) {
                throw new IllegalArgumentException("Facts cannot be null");
            }
        } catch (RuntimeException ex) {
            publish(new RuleEngineEvent(
                    RuleEngineEvent.Operation.EXECUTE,
                    ruleName,
                    false,
                    System.nanoTime() - started,
                    0,
                    ex.getMessage()));
            throw ex;
        }

        int firedRules = 0;
        RuntimeException failure = null;
        lifecycleLock.readLock().lock();
        KieSession session = null;
        try {
            KieContainer container = cache.get(normalizedName);
            if (container == null) {
                throw new IllegalStateException("Rule is not loaded: " + normalizedName);
            }

            session = container.newKieSession();
            if (globals != null) {
                for (Map.Entry<String, Object> entry : globals.entrySet()) {
                    session.setGlobal(entry.getKey(), entry.getValue());
                }
            }
            for (Object fact : facts) {
                if (fact == null) {
                    throw new IllegalArgumentException("Facts cannot contain null values");
                }
                session.insert(fact);
            }
            firedRules = session.fireAllRules();
        } catch (RuntimeException ex) {
            failure = ex;
        } catch (Exception ex) {
            failure = new IllegalStateException(
                    "Failed to execute rule [" + normalizedName + "]: " + ex.getMessage(), ex);
        } finally {
            if (session != null) {
                session.dispose();
            }
            lifecycleLock.readLock().unlock();
        }

        if (failure != null) {
            publish(new RuleEngineEvent(
                    RuleEngineEvent.Operation.EXECUTE,
                    normalizedName,
                    false,
                    System.nanoTime() - started,
                    0,
                    failure.getMessage()));
            throw failure;
        }

        publish(new RuleEngineEvent(
                RuleEngineEvent.Operation.EXECUTE,
                normalizedName,
                true,
                System.nanoTime() - started,
                firedRules,
                "executed"));
        return firedRules;
    }

    @Override
    public boolean contains(String ruleName) {
        return ruleName != null && cache.containsKey(ruleName);
    }

    @Override
    public Set<String> getLoadedRuleNames() {
        return Collections.unmodifiableSet(new HashSet<>(cache.keySet()));
    }

    @Override
    public void remove(String ruleName) {
        if (ruleName == null) {
            return;
        }
        lifecycleLock.writeLock().lock();
        try {
            disposeQuietly(cache.remove(ruleName));
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        lifecycleLock.writeLock().lock();
        try {
            for (KieContainer container : cache.values()) {
                disposeQuietly(container);
            }
            cache.clear();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        clear();
    }

    private CompiledRule compile(String ruleName, String drl) {
        String normalizedName;
        try {
            normalizedName = requireText(ruleName, "ruleName");
            requireText(drl, "drl");
        } catch (IllegalArgumentException ex) {
            return CompiledRule.failure(ex.getMessage());
        }

        String artifactId = sanitizeArtifactId(normalizedName);
        String version = versionPrefix + "." + System.currentTimeMillis()
                + "-" + buildSequence.incrementAndGet()
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        ReleaseId releaseId = kieServices.newReleaseId(releaseGroupId, artifactId, version);

        try {
            KieFileSystem fileSystem = kieServices.newKieFileSystem();
            fileSystem.generateAndWritePomXML(releaseId);

            Resource resource = kieServices.getResources()
                    .newByteArrayResource(drl.getBytes(StandardCharsets.UTF_8))
                    .setResourceType(ResourceType.DRL)
                    .setTargetPath("src/main/resources/rules/" + artifactId + ".drl");
            fileSystem.write(resource);

            KieBuilder builder = kieServices.newKieBuilder(fileSystem);
            builder.buildAll();

            if (builder.getResults().hasMessages(Message.Level.ERROR)) {
                removeModuleQuietly(releaseId);
                return CompiledRule.failure(joinMessages(builder.getResults().getMessages(Message.Level.ERROR)));
            }

            String warnings = builder.getResults().hasMessages(Message.Level.WARNING)
                    ? joinMessages(builder.getResults().getMessages(Message.Level.WARNING))
                    : null;
            KieContainer container = kieServices.newKieContainer(releaseId);
            String message = warnings == null || warnings.trim().isEmpty()
                    ? "compiled and activated"
                    : WARNING_PREFIX + warnings;
            return CompiledRule.success(container, message);
        } catch (Exception ex) {
            removeModuleQuietly(releaseId);
            logger.error("Failed to compile rule [{}]", normalizedName, ex);
            return CompiledRule.failure(exceptionMessage(ex));
        }
    }

    private void publish(RuleEngineEvent event) {
        for (RuleEngineListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ex) {
                logger.warn("RuleEngineListener [{}] failed and was isolated from the runtime",
                        listener.getClass().getName(), ex);
            }
        }
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
                builder.append('\n');
            }
            builder.append(message.getText());
        }
        return builder.length() == 0 ? "Drools build failed with no diagnostic message" : builder.toString();
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
        ReleaseId releaseId = null;
        try {
            releaseId = container.getReleaseId();
            container.dispose();
        } catch (Exception ex) {
            logger.debug("Failed to dispose KieContainer cleanly", ex);
        } finally {
            if (releaseId != null) {
                removeModuleQuietly(releaseId);
            }
        }
    }

    private void removeModuleQuietly(ReleaseId releaseId) {
        try {
            kieServices.getRepository().removeKieModule(releaseId);
        } catch (Exception ex) {
            logger.debug("Failed to remove KieModule [{}] from repository", releaseId, ex);
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be null or empty");
        }
        return value.trim();
    }

    private static final class CompiledRule {
        private final KieContainer container;
        private final RuleBuildResult result;

        private CompiledRule(KieContainer container, RuleBuildResult result) {
            this.container = container;
            this.result = result;
        }

        private static CompiledRule success(KieContainer container, String message) {
            return new CompiledRule(container, RuleBuildResult.success(message));
        }

        private static CompiledRule failure(String message) {
            return new CompiledRule(null, RuleBuildResult.failure(message));
        }
    }
}
