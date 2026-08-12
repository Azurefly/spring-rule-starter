# 动态规则加载说明

本项目的规则加载以 PostgreSQL 中的 `rule_meta` 为事实来源，JVM 内的 `KieContainer` 只作为可重建的执行缓存。完整安装、API、配置与安全边界请以根目录 [README.md](README.md) 和 [SECURITY.md](SECURITY.md) 为准。

## 加载原则

- 应用启动时加载所有 `status=ENABLED` 且 `type=DROOLS` 的规则。
- 每个规则名称对应一个当前激活的 `KieContainer`。
- 新版本 DRL 编译成功后才替换当前容器和数据库中的有效内容。
- 新版本编译失败时，最后一次成功的容器与数据库有效内容保持不变；失败候选会写入构建历史用于诊断。
- 禁用、删除或数据库中已不存在的规则会从本机缓存移除。
- `/api/rules/reload-all` 会先清理本机缓存，再根据数据库重建，避免失效规则长期残留。

## 版本与回滚

`rule_build_history` 保存每次重要构建尝试的版本、结果、内容和来源。

- 成功创建：记录版本 1 的完整 DRL 快照。
- 成功更新：业务版本递增并记录可回滚快照。
- 失败更新：不推进当前有效版本，但记录失败候选内容和目标版本号。
- 回滚：只能选择 `SUCCESS` 的历史快照，并以该内容创建一个新的当前版本，不覆盖旧历史。

## Redis 多节点刷新

Redis 默认关闭。需要多实例部署时设置：

```bash
RULE_REDIS_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6379
```

成功创建、更新、回滚、启停或删除后，操作节点会发布 `rule-refresh`。其他节点只更新自己的内存容器，不重复写共享构建历史。

Redis Pub/Sub 不是持久消息队列；节点漏掉消息后，可通过重启或 `/api/rules/reload-all` 从 PostgreSQL 重新对齐。

## 两种执行方式

### Order 示例兼容接口

```http
POST /api/rules/exec/{name}
Content-Type: application/json

{"amount": 150}
```

该接口保留原项目行为，会把输入转换为内置 `com.example.ruleengine.Order`。

### 通用 Map 事实接口

```http
POST /api/rules/exec-map/{name}
Content-Type: application/json

{"level": "VIP"}
```

该接口直接把 JSON 对象作为 `Map<String,Object>` 插入 Drools，适合不想依赖内置 `Order` DTO 的规则。

## 安全提醒

DRL 可以调用 Java 代码，应视为受信任的可执行代码，而不是面向不可信用户的安全表达式。不要在没有认证、授权和网络隔离的情况下直接开放规则编辑 API。
