package com.example.api.subscriber;

import com.example.api.drools.BuildResult;
import com.example.api.drools.KieManager;
import com.example.api.entity.RuleMeta;
import com.example.api.repository.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Cluster cache refresh listener. It intentionally does not write build history because every
 * cluster node receives the same event; writing here would create duplicate history records.
 */
@Component
@ConditionalOnProperty(prefix = "rule.redis", name = "enabled", havingValue = "true")
public class RuleRedisSubscriber implements MessageListener {
    private static final Logger logger = LoggerFactory.getLogger(RuleRedisSubscriber.class);

    @Autowired
    private RuleRepository repository;

    @Autowired
    private KieManager kieManager;

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        String name = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            RuleMeta meta = repository.findByName(name).orElse(null);
            if (meta == null || !"ENABLED".equalsIgnoreCase(meta.getStatus())) {
                kieManager.removeContainer(name);
                logger.info("Removed inactive rule [{}] from local cache", name);
                return;
            }

            BuildResult result = kieManager.buildOrUpdateReport(meta.getName(), meta.getContent());
            if (result.getStatus() == BuildResult.Status.SUCCESS) {
                logger.info("Refreshed rule [{}] from Redis notification", name);
            } else {
                logger.error("Failed to refresh rule [{}]: {}", name, result.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Failed to process rule refresh for [{}]", name, ex);
        }
    }
}
