package com.azurefly.rule.core;

/**
 * Lightweight extension point for metrics, tracing or audit adapters.
 * Listener failures are isolated from rule execution by the runtime.
 */
public interface RuleEngineListener {
    void onEvent(RuleEngineEvent event);
}
