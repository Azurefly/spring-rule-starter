package com.azurefly.rule.autoconfigure;

import com.azurefly.rule.core.RuleEngine;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SpringRuleMetricsAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SpringRuleMetricsAutoConfiguration.class,
                    SpringRuleAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void recordsLowCardinalityInstallAndExecutionMetrics() {
        contextRunner.run(context -> {
            RuleEngine ruleEngine = context.getBean(RuleEngine.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            assertThat(ruleEngine.install("metrics-test", "package rules; rule \"always\" when then end").isSuccess())
                    .isTrue();
            assertThat(ruleEngine.execute("metrics-test", new Object())).isEqualTo(1);

            Timer install = registry.find("spring.rule.operation")
                    .tag("operation", "install")
                    .tag("outcome", "success")
                    .timer();
            Timer execute = registry.find("spring.rule.operation")
                    .tag("operation", "execute")
                    .tag("outcome", "success")
                    .timer();
            DistributionSummary fired = registry.find("spring.rule.rules.fired").summary();

            assertThat(install).isNotNull();
            assertThat(install.count()).isEqualTo(1L);
            assertThat(execute).isNotNull();
            assertThat(execute.count()).isEqualTo(1L);
            assertThat(fired).isNotNull();
            assertThat(fired.totalAmount()).isEqualTo(1.0d);
        });
    }

    @Test
    void canDisableMetricsWithoutDisablingTheRuleEngine() {
        contextRunner.withPropertyValues("spring.rule.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(RuleEngine.class);
                    assertThat(context).doesNotHaveBean(MicrometerRuleEngineListener.class);
                });
    }
}
