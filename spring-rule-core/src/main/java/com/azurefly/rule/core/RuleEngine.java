package com.azurefly.rule.core;

import java.util.Map;
import java.util.Set;

/**
 * Public runtime API exposed by spring-rule-core.
 */
public interface RuleEngine extends AutoCloseable {
    RuleBuildResult validate(String ruleName, String drl);

    RuleBuildResult install(String ruleName, String drl);

    int execute(String ruleName, Object fact);

    int execute(String ruleName, Iterable<?> facts, Map<String, Object> globals);

    boolean contains(String ruleName);

    Set<String> getLoadedRuleNames();

    void remove(String ruleName);

    void clear();

    @Override
    void close();
}
