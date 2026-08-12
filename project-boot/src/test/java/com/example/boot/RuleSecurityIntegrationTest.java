package com.example.boot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rule.security.enabled=true",
        "rule.security.admin-api-key=admin-integration-key-0123456789",
        "rule.security.reader-api-key=reader-integration-key-0123456789",
        "management.endpoints.web.exposure.include=health,info,metrics"
})
@AutoConfigureMockMvc
class RuleSecurityIntegrationTest {
    private static final String HEADER = "X-Rule-Api-Key";
    private static final String ADMIN_KEY = "admin-integration-key-0123456789";
    private static final String READER_KEY = "reader-integration-key-0123456789";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthProbeRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void ruleApiRequiresAuthenticationWhenEnabled() throws Exception {
        mockMvc.perform(get("/api/rules/list"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/rules/list").header(HEADER, "invalid-integration-key-012345"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readerCanReadButCannotMutateRules() throws Exception {
        mockMvc.perform(get("/api/rules/list").header(HEADER, READER_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rules/reload-all").header(HEADER, READER_KEY))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanMutateRules() throws Exception {
        mockMvc.perform(post("/api/rules/reload-all").header(HEADER, ADMIN_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void nonHealthActuatorEndpointsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics").header(HEADER, READER_KEY))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics").header(HEADER, ADMIN_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void ruleRuntimeMetricsAreRecordedAndProtected() throws Exception {
        mockMvc.perform(post("/api/rules/validate")
                        .header(HEADER, ADMIN_KEY)
                        .param("name", "metrics-probe")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("package rules; rule \"metrics probe\" when then end"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/spring.rule.operation").header(HEADER, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("spring.rule.operation"));
    }
}
