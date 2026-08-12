package com.example.ruleengine.drools;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

/**
 * Helper for classpath-based Drools sessions declared in META-INF/kmodule.xml.
 * Dynamic database-backed rules are managed by project-api's KieManager instead.
 */
@Component
public class DroolsHelper {
    private final KieContainer kieContainer;

    public DroolsHelper() {
        KieServices kieServices = KieServices.Factory.get();
        if (kieServices == null) {
            throw new IllegalStateException("KieServices is not available; check Drools dependencies");
        }
        this.kieContainer = kieServices.getKieClasspathContainer();
        if (this.kieContainer == null) {
            throw new IllegalStateException("KieClasspathContainer could not be initialized");
        }
    }

    public void fireRules(Object fact, String kieSessionName) {
        if (fact == null) {
            throw new IllegalArgumentException("Fact cannot be null");
        }
        if (kieSessionName == null || kieSessionName.trim().isEmpty()) {
            throw new IllegalArgumentException("KieSession name cannot be null or empty");
        }

        KieSession session = null;
        try {
            session = kieContainer.newKieSession(kieSessionName);
            session.insert(fact);
            session.fireAllRules();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fire classpath rules: " + ex.getMessage(), ex);
        } finally {
            if (session != null) {
                session.dispose();
            }
        }
    }
}
