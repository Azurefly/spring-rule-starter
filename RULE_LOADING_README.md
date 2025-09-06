# 规则引擎数据库加载功能

## 概述

现在规则引擎支持从数据库动态加载规则，无需重启应用即可更新规则。

## 主要功能

### 1. 自动规则加载
- 应用启动时自动从数据库加载所有启用的DROOLS规则
- 只加载状态为"ENABLED"且类型为"DROOLS"的规则

### 2. 规则管理API

#### 上传规则
```bash
POST /api/rules/upload
Content-Type: multipart/form-data

name: 规则名称
type: DROOLS
file: 规则文件(.drl)
```

#### 列出所有规则
```bash
GET /api/rules/list
```

#### 执行规则
```bash
POST /api/rules/exec/{规则名称}
Content-Type: application/json

{
  "amount": 150.0
}
```

#### 重新加载指定规则
```bash
POST /api/rules/refresh/{规则名称}
```

#### 重新加载所有规则
```bash
POST /api/rules/reload-all
```

#### 更新规则内容
```bash
PUT /api/rules/{规则名称}
Content-Type: text/plain

规则内容...
```

### 3. 数据库表结构

```sql
CREATE TABLE rule_meta (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) UNIQUE,
  type VARCHAR(50),
  content TEXT,
  status VARCHAR(50),
  version INTEGER DEFAULT 1,
  last_build_status VARCHAR(50),
  last_build_message TEXT,
  last_build_at TIMESTAMP,
  created_by VARCHAR(100),
  created_at TIMESTAMP
);
```

### 4. 规则状态管理

- **ENABLED**: 规则启用，会被自动加载
- **DISABLED**: 规则禁用，不会被加载
- **DROOLS**: 规则类型，支持Drools规则引擎

### 5. 使用示例

#### 测试规则加载
1. 运行 `test_rule_loading.sql` 插入测试规则
2. 启动应用，查看控制台输出规则加载情况
3. 使用API测试规则执行

#### 规则内容示例
```drl
package rules;

import com.example.ruleengine.Order;

rule "Free Shipping"
when
    $o : Order(amount > 100)
then
    $o.setFreeShipping(true);
    System.out.println("Free shipping applied");
end
```

### 6. 热重载机制

- 规则更新后自动重新编译
- 支持Redis集群通知（如果配置了Redis）
- 规则执行时自动检查并加载缺失的规则

### 7. 错误处理

- 规则编译失败时记录错误信息
- 支持规则版本管理和回滚
- 提供详细的构建状态和错误消息

## 注意事项

1. 确保Order类有相应的setter方法（如setFreeShipping, setDiscount等）
2. 规则内容必须符合Drools DRL语法
3. 规则名称必须唯一
4. 建议在生产环境中定期备份规则数据
