package com.example.boot.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rule.security")
public class RuleSecurityProperties implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(RuleSecurityProperties.class);
    private static final int MIN_KEY_LENGTH = 16;

    private boolean enabled = false;
    private String headerName = "X-Rule-Api-Key";
    private String adminApiKey;
    private String readerApiKey;

    @Override
    public void afterPropertiesSet() {
        if (headerName == null || headerName.trim().isEmpty()) {
            throw new IllegalStateException("rule.security.header-name cannot be blank");
        }
        if (!enabled) {
            logger.warn("Rule management API authentication is DISABLED. Enable rule.security.enabled for controlled environments.");
            return;
        }
        validateKey(adminApiKey, "rule.security.admin-api-key", true);
        validateKey(readerApiKey, "rule.security.reader-api-key", false);
        logger.info("Rule management API authentication is enabled using header [{}]", headerName);
    }

    private void validateKey(String key, String propertyName, boolean required) {
        if (key == null || key.trim().isEmpty()) {
            if (required) {
                throw new IllegalStateException(propertyName + " is required when rule.security.enabled=true");
            }
            return;
        }
        if (key.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(propertyName + " must contain at least " + MIN_KEY_LENGTH + " characters");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }
    public String getAdminApiKey() { return adminApiKey; }
    public void setAdminApiKey(String adminApiKey) { this.adminApiKey = adminApiKey; }
    public String getReaderApiKey() { return readerApiKey; }
    public void setReaderApiKey(String readerApiKey) { this.readerApiKey = readerApiKey; }
}
