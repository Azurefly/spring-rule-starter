package com.example.api.drools;

import com.example.ruleengine.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KieManagerTest {
    private final KieManager kieManager = new KieManager();

    @AfterEach
    void tearDown() {
        kieManager.clearContainers();
    }

    @Test
    void validRuleBuildsAndExecutes() {
        String drl = "package rules;\n"
                + "import com.example.ruleengine.Order;\n"
                + "rule \"free-shipping\"\n"
                + "when\n"
                + "  $o : Order(amount > 100)\n"
                + "then\n"
                + "  $o.setFreeShipping(true);\n"
                + "end\n";

        BuildResult build = kieManager.buildOrUpdateReport("shipping-rule", drl);
        assertEquals(BuildResult.Status.SUCCESS, build.getStatus(), build.getMessage());

        Order order = new Order(150);
        int fired = kieManager.fireRulesAndCount("shipping-rule", order);
        assertEquals(1, fired);
        assertTrue(order.isFreeShipping());
    }

    @Test
    void failedReplacementKeepsLastSuccessfulContainer() {
        String validDrl = "package rules;\n"
                + "import com.example.ruleengine.Order;\n"
                + "rule \"discount\" when $o : Order(amount > 100) then $o.setDiscount(5.0); end\n";

        BuildResult first = kieManager.buildOrUpdateReport("safe-replace", validDrl);
        assertEquals(BuildResult.Status.SUCCESS, first.getStatus(), first.getMessage());

        BuildResult failed = kieManager.buildOrUpdateReport("safe-replace", "this is not valid drl");
        assertEquals(BuildResult.Status.FAILURE, failed.getStatus());
        assertTrue(kieManager.hasContainer("safe-replace"));

        Order order = new Order(200);
        kieManager.fireRules("safe-replace", order);
        assertEquals(5.0, order.getDiscount(), 0.001);
    }

    @Test
    void validationDoesNotActivateRule() {
        String drl = "package rules; rule \"always\" when then end";
        BuildResult result = kieManager.validateReport("validate-only", drl);
        assertEquals(BuildResult.Status.SUCCESS, result.getStatus(), result.getMessage());
        assertFalse(kieManager.hasContainer("validate-only"));
    }
}
