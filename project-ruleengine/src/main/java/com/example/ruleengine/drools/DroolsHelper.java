
package com.example.ruleengine.drools;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

@Component
public class DroolsHelper {
    private final KieContainer kieContainer;

    public DroolsHelper() {
        KieContainer tempContainer = null;
        try {
            System.out.println("Initializing DroolsHelper...");
            KieServices ks = KieServices.Factory.get();
            System.out.println("KieServices: " + ks);
            if (ks == null) {
                System.err.println("KieServices.Factory.get() returned null. Check Drools dependencies.");
                tempContainer = null;
            } else {
                tempContainer = ks.getKieClasspathContainer();
                System.out.println("KieContainer: " + tempContainer);
                if (tempContainer == null) {
                    System.err.println("KieClasspathContainer is null. Check Drools configuration.");
                } else {
                    System.out.println("DroolsHelper initialized successfully");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize DroolsHelper: " + e.getMessage());
            e.printStackTrace();
            tempContainer = null;
        }
        this.kieContainer = tempContainer;
    }

    public void fireRules(Object fact, String kieSessionName) {
        if (kieContainer == null) {
            throw new RuntimeException("KieContainer is not initialized");
        }
        if (fact == null) {
            throw new RuntimeException("Fact cannot be null");
        }
        if (kieSessionName == null || kieSessionName.trim().isEmpty()) {
            throw new RuntimeException("KieSession name cannot be null or empty");
        }
        KieSession session = null;
        try {
            session = kieContainer.newKieSession(kieSessionName);
            session.insert(fact);
            session.fireAllRules();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fire rules: " + ex.getMessage(), ex);
        } finally {
            if (session != null) {
                session.dispose();
            }
        }
    }
}
