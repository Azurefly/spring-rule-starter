package com.example.boot.health;

import com.azurefly.rule.core.RuleEngine;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Standard Actuator health contribution for the in-memory rule runtime.
 */
@Component("ruleEngine")
public class RuleEngineHealthIndicator implements HealthIndicator {
    private final RuleEngine ruleEngine;

    public RuleEngineHealthIndicator(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    @Override
    public Health health() {
        try {
            return Health.up()
                    .withDetail("loadedRules", ruleEngine.getLoadedRuleNames().size())
                    .withDetail("runtime", ruleEngine.getClass().getSimpleName())
                    .build();
        } catch (RuntimeException ex) {
            return Health.down(ex).build();
        }
    }
}
