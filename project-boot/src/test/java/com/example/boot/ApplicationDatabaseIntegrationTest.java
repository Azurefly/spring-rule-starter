package com.example.boot;

import com.azurefly.rule.core.RuleEngine;
import com.example.boot.health.RuleEngineHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationDatabaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private RuleEngineHealthIndicator ruleEngineHealthIndicator;

    @Test
    void flywayCreatesAndOwnsTheRuleSchema() {
        Integer ruleMetaTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'rule_meta'",
                Integer.class);
        Integer historyTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'rule_build_history'",
                Integer.class);
        Integer successfulMigration = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success = true",
                Integer.class);

        assertThat(ruleMetaTables).isEqualTo(1);
        assertThat(historyTables).isEqualTo(1);
        assertThat(successfulMigration).isEqualTo(1);
    }

    @Test
    void actuatorHealthIncludesTheRuleRuntime() {
        assertThat(ruleEngine).isNotNull();
        assertThat(ruleEngineHealthIndicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(ruleEngineHealthIndicator.health().getDetails())
                .containsEntry("loadedRules", ruleEngine.getLoadedRuleNames().size());
    }
}
