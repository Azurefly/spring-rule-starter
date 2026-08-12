package com.azurefly.rule.autoconfigure;

import com.azurefly.rule.core.DroolsRuleEngine;
import com.azurefly.rule.core.RuleEngine;
import com.azurefly.rule.core.RuleEngineListener;
import org.kie.api.KieServices;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.stream.Collectors;

@AutoConfiguration
@ConditionalOnClass({RuleEngine.class, KieServices.class})
@ConditionalOnProperty(prefix = "spring.rule", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SpringRuleProperties.class)
public class SpringRuleAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(RuleEngine.class)
    public RuleEngine ruleEngine(SpringRuleProperties properties,
                                 ObjectProvider<RuleEngineListener> listenerProvider) {
        List<RuleEngineListener> listeners = listenerProvider.orderedStream().collect(Collectors.toList());
        return new DroolsRuleEngine(
                properties.getReleaseGroupId(),
                properties.getVersionPrefix(),
                listeners);
    }
}
