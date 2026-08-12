package com.azurefly.rule.autoconfigure;

import com.azurefly.rule.core.RuleEngineEvent;
import com.azurefly.rule.core.RuleEngineListener;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Low-cardinality Micrometer adapter for RuleEngine runtime events.
 * Rule names are intentionally not used as metric tags.
 */
public class MicrometerRuleEngineListener implements RuleEngineListener {
    private final MeterRegistry meterRegistry;

    public MicrometerRuleEngineListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onEvent(RuleEngineEvent event) {
        String operation = event.getOperation().name().toLowerCase(Locale.ROOT);
        String outcome = event.isSuccess() ? "success" : "failure";

        Timer.builder("spring.rule.operation")
                .description("Rule validation, installation and execution latency")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(event.getDurationNanos(), TimeUnit.NANOSECONDS);

        if (event.getOperation() == RuleEngineEvent.Operation.EXECUTE && event.isSuccess()) {
            DistributionSummary.builder("spring.rule.rules.fired")
                    .description("Number of Drools rules fired per successful execution")
                    .baseUnit("rules")
                    .register(meterRegistry)
                    .record(event.getFiredRules());
        }
    }
}
