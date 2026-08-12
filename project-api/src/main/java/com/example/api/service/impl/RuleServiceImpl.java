package com.example.api.service.impl;

import com.example.api.drools.BuildResult;
import com.example.api.drools.KieManager;
import com.example.api.entity.RuleBuildHistory;
import com.example.api.entity.RuleMeta;
import com.example.api.repository.RuleBuildHistoryRepository;
import com.example.api.repository.RuleRepository;
import com.example.api.service.RuleService;
import com.example.common.Result;
import com.example.ruleengine.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Transactional
public class RuleServiceImpl implements RuleService {
    private static final Logger logger = LoggerFactory.getLogger(RuleServiceImpl.class);
    private static final Pattern RULE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,119}");

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired(required = false)
    private RuleBuildHistoryRepository historyRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private KieManager kieManager;

    @Value("${rule.redis.enabled:false}")
    private boolean redisEnabled;

    @Override
    public Result<Void> saveRule(String name, String type, MultipartFile contentFile) throws Exception {
        String ruleName = requireValidName(name);
        String ruleType = normalizeType(type);
        if (contentFile == null || contentFile.isEmpty()) {
            return Result.fail("Rule file cannot be empty");
        }
        if (ruleRepository.findByName(ruleName).isPresent()) {
            return Result.fail("Rule already exists: " + ruleName);
        }

        String content = readUtf8(contentFile);
        if (content.trim().isEmpty()) {
            return Result.fail("Rule content cannot be empty");
        }

        BuildResult build = kieManager.buildOrUpdateReport(ruleName, content);
        if (build.getStatus() != BuildResult.Status.SUCCESS) {
            return Result.fail(build.getMessage());
        }

        try {
            RuleMeta meta = new RuleMeta();
            meta.setName(ruleName);
            meta.setType(ruleType);
            meta.setContent(content);
            meta.setStatus("ENABLED");
            meta.setVersion(1);
            applyBuildResult(meta, build);
            ruleRepository.save(meta);
            recordHistory(meta, content, build, "create");
            publishRefresh(ruleName);
            return Result.success();
        } catch (RuntimeException ex) {
            kieManager.removeContainer(ruleName);
            throw ex;
        }
    }

    @Override
    public Result<List<RuleMeta>> listRules() {
        return Result.success(ruleRepository.findAllByOrderByUpdatedAtDesc());
    }

    @Override
    public Result<Object> executeByName(String name, Map<String, Object> fact) throws Exception {
        RuleMeta meta = requireExecutableRule(name);
        if (fact == null) {
            return Result.fail("Fact cannot be null");
        }

        Object amountObject = fact.get("amount");
        if (amountObject == null) {
            return Result.fail("The legacy Order execution endpoint requires an 'amount' field");
        }

        double amount;
        try {
            amount = amountObject instanceof Number
                    ? ((Number) amountObject).doubleValue()
                    : Double.parseDouble(String.valueOf(amountObject));
        } catch (NumberFormatException ex) {
            return Result.fail("amount must be numeric");
        }

        ensureContainer(meta);
        Order order = new Order(amount);
        kieManager.fireRules(meta.getName(), order);
        return Result.success(order);
    }

    @Override
    public Result<Map<String, Object>> executeMapByName(String name, Map<String, Object> fact) throws Exception {
        RuleMeta meta = requireExecutableRule(name);
        if (fact == null) {
            return Result.fail("Fact cannot be null");
        }
        ensureContainer(meta);
        Map<String, Object> mutableFact = new LinkedHashMap<>(fact);
        kieManager.fireRules(meta.getName(), mutableFact);
        return Result.success(mutableFact);
    }

    @Override
    public Result<Void> updateRuleContent(String name, String content) throws Exception {
        String ruleName = requireValidName(name);
        if (content == null || content.trim().isEmpty()) {
            return Result.fail("Rule content cannot be empty");
        }

        RuleMeta meta = ruleRepository.findByName(ruleName)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleName));
        ensureDroolsType(meta);

        int nextVersion = meta.getVersion() == null ? 1 : meta.getVersion() + 1;
        String oldContent = meta.getContent();
        boolean wasEnabled = "ENABLED".equalsIgnoreCase(meta.getStatus());

        BuildResult build = kieManager.buildOrUpdateReport(ruleName, content);
        if (build.getStatus() != BuildResult.Status.SUCCESS) {
            applyBuildResult(meta, build);
            ruleRepository.save(meta);
            recordHistory(ruleName, nextVersion, content, build, "update-validation");
            return Result.fail(build.getMessage());
        }

        try {
            meta.setContent(content);
            meta.setVersion(nextVersion);
            applyBuildResult(meta, build);
            ruleRepository.save(meta);
            recordHistory(meta, content, build, "update");
            if (!wasEnabled) {
                kieManager.removeContainer(ruleName);
            }
            publishRefresh(ruleName);
            return Result.success();
        } catch (RuntimeException ex) {
            restoreContainer(ruleName, oldContent, wasEnabled);
            throw ex;
        }
    }

    @Override
    public Result<Void> validateRule(String name, String content) {
        try {
            String ruleName = requireValidName(name);
            if (content == null || content.trim().isEmpty()) {
                return Result.fail("Rule content cannot be empty");
            }
            BuildResult result = kieManager.validateReport(ruleName, content);
            return result.getStatus() == BuildResult.Status.SUCCESS
                    ? Result.success()
                    : Result.fail(result.getMessage());
        } catch (Exception ex) {
            return Result.fail(ex.getMessage());
        }
    }

    @Override
    public Result<Void> setRuleStatus(String name, String status) {
        String ruleName = requireValidName(name);
        String normalizedStatus = normalizeStatus(status);
        RuleMeta meta = ruleRepository.findByName(ruleName)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleName));

        if (normalizedStatus.equalsIgnoreCase(meta.getStatus())) {
            return Result.success();
        }

        if ("DISABLED".equals(normalizedStatus)) {
            meta.setStatus("DISABLED");
            ruleRepository.save(meta);
            kieManager.removeContainer(ruleName);
            publishRefresh(ruleName);
            return Result.success();
        }

        ensureDroolsType(meta);
        BuildResult build = kieManager.buildOrUpdateReport(ruleName, meta.getContent());
        applyBuildResult(meta, build);
        if (build.getStatus() != BuildResult.Status.SUCCESS) {
            ruleRepository.save(meta);
            recordHistory(meta, meta.getContent(), build, "enable-validation");
            return Result.fail(build.getMessage());
        }

        meta.setStatus("ENABLED");
        ruleRepository.save(meta);
        recordHistory(meta, meta.getContent(), build, "enable");
        publishRefresh(ruleName);
        return Result.success();
    }

    @Override
    public Result<Void> deleteRule(String name) {
        String ruleName = requireValidName(name);
        RuleMeta meta = ruleRepository.findByName(ruleName).orElse(null);
        if (meta == null) {
            return Result.fail("Rule not found: " + ruleName);
        }

        if (historyRepository != null) {
            historyRepository.deleteByRuleName(ruleName);
        }
        ruleRepository.delete(meta);
        kieManager.removeContainer(ruleName);
        publishRefresh(ruleName);
        return Result.success();
    }

    @Override
    public Result<RuleMeta> getRule(String name) {
        try {
            String ruleName = requireValidName(name);
            Optional<RuleMeta> meta = ruleRepository.findByName(ruleName);
            return meta.isPresent() ? Result.success(meta.get()) : Result.fail("Rule not found: " + ruleName);
        } catch (Exception ex) {
            return Result.fail(ex.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> getBuildHistoryPage(String name, int page, int size) {
        try {
            String ruleName = requireValidName(name);
            if (historyRepository == null) {
                return Result.fail("History repository is not available");
            }
            int safePage = Math.max(0, page);
            int safeSize = Math.min(100, Math.max(1, size));
            Pageable pageable = PageRequest.of(safePage, safeSize);
            Page<RuleBuildHistory> history = historyRepository.findByRuleNameOrderByBuiltAtDesc(ruleName, pageable);
            Map<String, Object> response = new HashMap<>();
            response.put("items", history.getContent());
            response.put("total", history.getTotalElements());
            response.put("page", history.getNumber());
            response.put("size", history.getSize());
            return Result.success(response);
        } catch (Exception ex) {
            return Result.fail(ex.getMessage());
        }
    }

    @Override
    public Result<List<RuleBuildHistory>> getBuildHistory(String name) {
        try {
            String ruleName = requireValidName(name);
            if (historyRepository == null) {
                return Result.fail("History repository is not available");
            }
            return Result.success(historyRepository.findByRuleNameOrderByBuiltAtDesc(ruleName));
        } catch (Exception ex) {
            return Result.fail(ex.getMessage());
        }
    }

    @Override
    public Result<Void> rollbackRuleToVersion(String name, Integer version) throws Exception {
        String ruleName = requireValidName(name);
        if (version == null || version < 1) {
            return Result.fail("Version must be a positive integer");
        }
        if (historyRepository == null) {
            return Result.fail("History repository is not available");
        }

        RuleMeta meta = ruleRepository.findByName(ruleName)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleName));
        RuleBuildHistory target = historyRepository
                .findFirstByRuleNameAndVersionAndStatusOrderByBuiltAtDesc(ruleName, version, BuildResult.Status.SUCCESS.name())
                .orElse(null);
        if (target == null || target.getContent() == null || target.getContent().trim().isEmpty()) {
            return Result.fail("No successful rollback snapshot found for version: " + version);
        }

        int nextVersion = meta.getVersion() == null ? 1 : meta.getVersion() + 1;
        String oldContent = meta.getContent();
        boolean wasEnabled = "ENABLED".equalsIgnoreCase(meta.getStatus());
        BuildResult build = kieManager.buildOrUpdateReport(ruleName, target.getContent());
        if (build.getStatus() != BuildResult.Status.SUCCESS) {
            recordHistory(ruleName, nextVersion, target.getContent(), build, "rollback-validation");
            return Result.fail("Historical rule no longer compiles: " + build.getMessage());
        }

        try {
            meta.setContent(target.getContent());
            meta.setVersion(nextVersion);
            applyBuildResult(meta, build);
            ruleRepository.save(meta);
            recordHistory(meta, meta.getContent(), build, "rollback:v" + version);
            if (!wasEnabled) {
                kieManager.removeContainer(ruleName);
            }
            publishRefresh(ruleName);
            return Result.success();
        } catch (RuntimeException ex) {
            restoreContainer(ruleName, oldContent, wasEnabled);
            throw ex;
        }
    }

    @Override
    public Result<Set<String>> getLoadedRuleNames() {
        return Result.success(kieManager.getLoadedRuleNames());
    }

    private RuleMeta requireExecutableRule(String name) {
        String ruleName = requireValidName(name);
        RuleMeta meta = ruleRepository.findByName(ruleName)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleName));
        ensureDroolsType(meta);
        if (!"ENABLED".equalsIgnoreCase(meta.getStatus())) {
            throw new IllegalStateException("Rule is disabled: " + ruleName);
        }
        return meta;
    }

    private void ensureContainer(RuleMeta meta) {
        if (kieManager.hasContainer(meta.getName())) {
            return;
        }
        BuildResult load = kieManager.loadRuleFromDatabase(meta.getName());
        if (load.getStatus() != BuildResult.Status.SUCCESS) {
            throw new IllegalStateException("Failed to load rule: " + load.getMessage());
        }
    }

    private void ensureDroolsType(RuleMeta meta) {
        if (!"DROOLS".equalsIgnoreCase(meta.getType())) {
            throw new IllegalArgumentException("Unsupported rule type: " + meta.getType());
        }
    }

    private String requireValidName(String name) {
        if (name == null || !RULE_NAME_PATTERN.matcher(name.trim()).matches()) {
            throw new IllegalArgumentException("Rule name must match [A-Za-z0-9][A-Za-z0-9._-]{0,119}");
        }
        return name.trim();
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "DROOLS" : type.trim().toUpperCase();
        if (!"DROOLS".equals(normalized)) {
            throw new IllegalArgumentException("Only DROOLS rules are currently supported");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        String normalized = status.trim().toUpperCase();
        if (!"ENABLED".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException("Status must be ENABLED or DISABLED");
        }
        return normalized;
    }

    private String readUtf8(MultipartFile file) throws Exception {
        try (InputStream input = file.getInputStream();
             Scanner scanner = new Scanner(input, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private void applyBuildResult(RuleMeta meta, BuildResult build) {
        meta.setLastBuildAt(LocalDateTime.now());
        meta.setLastBuildStatus(build.getStatus().name());
        meta.setLastBuildMessage(build.getMessage());
    }

    private void recordHistory(RuleMeta meta, String content, BuildResult build, String builtBy) {
        recordHistory(meta.getName(), meta.getVersion(), content, build, builtBy);
    }

    private void recordHistory(String ruleName, Integer version, String content, BuildResult build, String builtBy) {
        if (historyRepository == null) {
            return;
        }
        RuleBuildHistory history = new RuleBuildHistory();
        history.setRuleName(ruleName);
        history.setVersion(version);
        history.setStatus(build.getStatus().name());
        history.setMessage(build.getMessage());
        history.setContent(content);
        history.setBuiltBy(builtBy);
        historyRepository.save(history);
    }

    private void publishRefresh(String ruleName) {
        if (!redisEnabled || redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.convertAndSend("rule-refresh", ruleName);
        } catch (Exception ex) {
            logger.warn("Rule [{}] was saved, but Redis refresh notification failed: {}", ruleName, ex.getMessage());
        }
    }

    private void restoreContainer(String ruleName, String oldContent, boolean shouldBeEnabled) {
        if (!shouldBeEnabled || oldContent == null || oldContent.trim().isEmpty()) {
            kieManager.removeContainer(ruleName);
            return;
        }
        BuildResult restore = kieManager.buildOrUpdateReport(ruleName, oldContent);
        if (restore.getStatus() != BuildResult.Status.SUCCESS) {
            logger.error("Failed to restore previous active container for rule [{}]: {}", ruleName, restore.getMessage());
        }
    }
}
