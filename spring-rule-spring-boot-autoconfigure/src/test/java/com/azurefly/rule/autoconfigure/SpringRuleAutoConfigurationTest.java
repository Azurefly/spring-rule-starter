package com.azurefly.rule.autoconfigure;

import com.azurefly.rule.core.RuleEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SpringRuleAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringRuleAutoConfiguration.class));

    @Test
    void registersRuleEngineByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RuleEngine.class);
            assertThat(context).hasSingleBean(SpringRuleProperties.class);
        });
    }

    @Test
    void canBeDisabled() {
        contextRunner.withPropertyValues("spring.rule.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RuleEngine.class));
    }

    @Test
    void backsOffForUserProvidedEngine() {
        contextRunner.withBean(RuleEngine.class, StubRuleEngine::new)
                .run(context -> assertThat(context).hasSingleBean(RuleEngine.class)
                        .getBean(RuleEngine.class).isInstanceOf(StubRuleEngine.class));
    }

    private static class StubRuleEngine implements RuleEngine {
        @Override public com.azurefly.rule.core.RuleBuildResult validate(String ruleName, String drl) { return null; }
        @Override public com.azurefly.rule.core.RuleBuildResult install(String ruleName, String drl) { return null; }
        @Override public int execute(String ruleName, Object fact) { return 0; }
        @Override public int execute(String ruleName, Iterable<?> facts, java.util.Map<String, Object> globals) { return 0; }
        @Override public boolean contains(String ruleName) { return false; }
        @Override public java.util.Set<String> getLoadedRuleNames() { return java.util.Collections.emptySet(); }
        @Override public void remove(String ruleName) { }
        @Override public void clear() { }
        @Override public void close() { }
    }
}
