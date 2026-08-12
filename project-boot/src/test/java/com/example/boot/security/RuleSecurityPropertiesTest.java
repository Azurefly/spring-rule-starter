package com.example.boot.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleSecurityPropertiesTest {

    @Test
    void disabledSecurityPreservesCompatibilityWithoutAKey() {
        RuleSecurityProperties properties = new RuleSecurityProperties();
        properties.setEnabled(false);
        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void enabledSecurityRequiresAStrongAdminKey() {
        RuleSecurityProperties missing = new RuleSecurityProperties();
        missing.setEnabled(true);
        assertThrows(IllegalStateException.class, missing::afterPropertiesSet);

        RuleSecurityProperties shortKey = new RuleSecurityProperties();
        shortKey.setEnabled(true);
        shortKey.setAdminApiKey("too-short");
        assertThrows(IllegalStateException.class, shortKey::afterPropertiesSet);
    }

    @Test
    void readerKeyIsOptionalButValidatedWhenPresent() {
        RuleSecurityProperties adminOnly = new RuleSecurityProperties();
        adminOnly.setEnabled(true);
        adminOnly.setAdminApiKey("admin-key-0123456789abcdef");
        assertDoesNotThrow(adminOnly::afterPropertiesSet);

        RuleSecurityProperties invalidReader = new RuleSecurityProperties();
        invalidReader.setEnabled(true);
        invalidReader.setAdminApiKey("admin-key-0123456789abcdef");
        invalidReader.setReaderApiKey("short");
        assertThrows(IllegalStateException.class, invalidReader::afterPropertiesSet);
    }
}
