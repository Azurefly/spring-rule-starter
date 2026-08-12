package com.azurefly.rule.autoconfigure;

import com.azurefly.rule.core.RuleEngineListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = SpringRuleAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "spring.rule.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringRuleMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MicrometerRuleEngineListener.class)
    public RuleEngineListener micrometerRuleEngineListener(ObjectProvider<MeterRegistry> meterRegistries) {
        return new MicrometerRuleEngineListener(meterRegistries);
    }
}
