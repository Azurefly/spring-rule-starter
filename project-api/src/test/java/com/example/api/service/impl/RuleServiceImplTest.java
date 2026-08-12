package com.example.api.service.impl;

import com.example.api.drools.BuildResult;
import com.example.api.drools.KieManager;
import com.example.api.entity.RuleBuildHistory;
import com.example.api.entity.RuleMeta;
import com.example.api.repository.RuleBuildHistoryRepository;
import com.example.api.repository.RuleRepository;
import com.example.common.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleServiceImplTest {

    @Mock
    private RuleRepository ruleRepository;

    @Mock
    private RuleBuildHistoryRepository historyRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private KieManager kieManager;

    @InjectMocks
    private RuleServiceImpl service;

    @Test
    void createStoresRollbackContentInHistory() throws Exception {
        String drl = "package rules; rule \"ok\" when then end";
        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.drl", "text/plain", drl.getBytes(StandardCharsets.UTF_8));

        when(ruleRepository.findByName("demo-rule")).thenReturn(Optional.empty());
        when(kieManager.buildOrUpdateReport("demo-rule", drl))
                .thenReturn(new BuildResult(BuildResult.Status.SUCCESS, "built"));
        when(ruleRepository.save(any(RuleMeta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Result<Void> result = service.saveRule("demo-rule", "DROOLS", file);

        assertEquals(200, result.getCode());
        ArgumentCaptor<RuleBuildHistory> history = ArgumentCaptor.forClass(RuleBuildHistory.class);
        verify(historyRepository).save(history.capture());
        assertEquals(1, history.getValue().getVersion());
        assertEquals(drl, history.getValue().getContent());
        assertEquals("SUCCESS", history.getValue().getStatus());
    }

    @Test
    void failedUpdateKeepsLastValidContentAndVersion() throws Exception {
        RuleMeta meta = activeRule("demo-rule", "old-valid-content", 1);
        String invalidCandidate = "not valid drl";

        when(ruleRepository.findByName("demo-rule")).thenReturn(Optional.of(meta));
        when(kieManager.buildOrUpdateReport("demo-rule", invalidCandidate))
                .thenReturn(new BuildResult(BuildResult.Status.FAILURE, "compile error"));
        when(ruleRepository.save(any(RuleMeta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Result<Void> result = service.updateRuleContent("demo-rule", invalidCandidate);

        assertEquals(500, result.getCode());
        assertEquals("old-valid-content", meta.getContent());
        assertEquals(1, meta.getVersion());
        assertEquals("FAILURE", meta.getLastBuildStatus());

        ArgumentCaptor<RuleBuildHistory> history = ArgumentCaptor.forClass(RuleBuildHistory.class);
        verify(historyRepository).save(history.capture());
        assertEquals(2, history.getValue().getVersion());
        assertEquals(invalidCandidate, history.getValue().getContent());
        assertEquals("FAILURE", history.getValue().getStatus());
    }

    @Test
    void rollbackCreatesANewVersionFromSuccessfulSnapshot() throws Exception {
        RuleMeta meta = activeRule("demo-rule", "current-content", 2);
        RuleBuildHistory target = new RuleBuildHistory();
        target.setRuleName("demo-rule");
        target.setVersion(1);
        target.setStatus("SUCCESS");
        target.setContent("historical-valid-content");

        when(ruleRepository.findByName("demo-rule")).thenReturn(Optional.of(meta));
        when(historyRepository.findFirstByRuleNameAndVersionAndStatusOrderByBuiltAtDesc(
                "demo-rule", 1, "SUCCESS")).thenReturn(Optional.of(target));
        when(kieManager.buildOrUpdateReport("demo-rule", "historical-valid-content"))
                .thenReturn(new BuildResult(BuildResult.Status.SUCCESS, "built"));
        when(ruleRepository.save(any(RuleMeta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Result<Void> result = service.rollbackRuleToVersion("demo-rule", 1);

        assertEquals(200, result.getCode());
        assertEquals(3, meta.getVersion());
        assertEquals("historical-valid-content", meta.getContent());
        assertEquals("SUCCESS", meta.getLastBuildStatus());
        assertNotNull(meta.getLastBuildAt());
    }

    private RuleMeta activeRule(String name, String content, int version) {
        RuleMeta meta = new RuleMeta();
        meta.setName(name);
        meta.setType("DROOLS");
        meta.setStatus("ENABLED");
        meta.setContent(content);
        meta.setVersion(version);
        return meta;
    }
}
