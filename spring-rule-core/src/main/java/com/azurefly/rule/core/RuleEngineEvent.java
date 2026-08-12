package com.azurefly.rule.core;

/**
 * Immutable runtime event emitted by {@link DroolsRuleEngine} for optional
 * observability integrations. The core module deliberately has no dependency
 * on a metrics implementation.
 */
public final class RuleEngineEvent {
    public enum Operation {
        VALIDATE,
        INSTALL,
        EXECUTE
    }

    private final Operation operation;
    private final String ruleName;
    private final boolean success;
    private final long durationNanos;
    private final int firedRules;
    private final String message;

    public RuleEngineEvent(Operation operation,
                           String ruleName,
                           boolean success,
                           long durationNanos,
                           int firedRules,
                           String message) {
        this.operation = operation;
        this.ruleName = ruleName;
        this.success = success;
        this.durationNanos = durationNanos;
        this.firedRules = firedRules;
        this.message = message;
    }

    public Operation getOperation() { return operation; }
    public String getRuleName() { return ruleName; }
    public boolean isSuccess() { return success; }
    public long getDurationNanos() { return durationNanos; }
    public int getFiredRules() { return firedRules; }
    public String getMessage() { return message; }
}
