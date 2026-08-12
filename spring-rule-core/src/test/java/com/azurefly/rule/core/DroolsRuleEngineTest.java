package com.azurefly.rule.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroolsRuleEngineTest {
    private final DroolsRuleEngine engine = new DroolsRuleEngine();

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void installsExecutesAndRemovesRule() {
        String drl = "package rules;\n"
                + "import com.azurefly.rule.core.TestFact;\n"
                + "rule \"accept\" when $f : TestFact(amount > 100) then $f.setAccepted(true); end\n";

        RuleBuildResult result = engine.install("accept-rule", drl);
        assertTrue(result.isSuccess(), result.getMessage());
        assertTrue(engine.contains("accept-rule"));

        TestFact fact = new TestFact(120);
        assertEquals(1, engine.execute("accept-rule", fact));
        assertTrue(fact.isAccepted());

        engine.remove("accept-rule");
        assertFalse(engine.contains("accept-rule"));
    }

    @Test
    void validationDoesNotInstallRule() {
        RuleBuildResult result = engine.validate("validation-only", "package rules; rule \"ok\" when then end");
        assertTrue(result.isSuccess(), result.getMessage());
        assertFalse(engine.contains("validation-only"));
    }

    @Test
    void invalidReplacementKeepsLastKnownGoodContainer() {
        String valid = "package rules;\n"
                + "import com.azurefly.rule.core.TestFact;\n"
                + "rule \"accept\" when $f : TestFact(amount > 100) then $f.setAccepted(true); end\n";
        assertTrue(engine.install("safe", valid).isSuccess());

        RuleBuildResult invalid = engine.install("safe", "not valid drl");
        assertFalse(invalid.isSuccess());
        assertTrue(engine.contains("safe"));

        TestFact fact = new TestFact(200);
        assertEquals(1, engine.execute("safe", fact));
        assertTrue(fact.isAccepted());
    }
}
