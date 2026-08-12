package com.azurefly.rule.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.rule")
public class SpringRuleProperties {
    /** Enable automatic RuleEngine registration. */
    private boolean enabled = true;

    /** Maven groupId used for dynamically compiled in-memory rule modules. */
    private String releaseGroupId = "com.azurefly.rules";

    /** Prefix used when generating unique dynamic rule module versions. */
    private String versionPrefix = "1.0";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getReleaseGroupId() {
        return releaseGroupId;
    }

    public void setReleaseGroupId(String releaseGroupId) {
        this.releaseGroupId = releaseGroupId;
    }

    public String getVersionPrefix() {
        return versionPrefix;
    }

    public void setVersionPrefix(String versionPrefix) {
        this.versionPrefix = versionPrefix;
    }
}
