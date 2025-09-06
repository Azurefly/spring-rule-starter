-- 规则元数据表
CREATE TABLE IF NOT EXISTS rule_meta (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL DEFAULT 'DROOLS',
    content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    version INTEGER DEFAULT 1,
    last_build_at TIMESTAMP,
    last_build_status VARCHAR(20),
    last_build_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 规则构建历史表
CREATE TABLE IF NOT EXISTS rule_build_history (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    message TEXT,
    content TEXT,
    built_by VARCHAR(100),
    built_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rule_name) REFERENCES rule_meta(name) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_rule_meta_name ON rule_meta(name);
CREATE INDEX IF NOT EXISTS idx_rule_meta_status ON rule_meta(status);
CREATE INDEX IF NOT EXISTS idx_rule_build_history_rule_name ON rule_build_history(rule_name);
CREATE INDEX IF NOT EXISTS idx_rule_build_history_built_at ON rule_build_history(built_at);

-- 插入示例规则
INSERT INTO rule_meta (name, type, content, status, version) VALUES 
('discount-rule', 'DROOLS', 
'package com.example.rules;

import com.example.ruleengine.Order;

rule "Discount Rule"
    when
        $order : Order(amount > 100)
    then
        $order.setDiscount(10.0);
        $order.setFreeShipping(true);
        System.out.println("Applied discount: " + $order.getDiscount());
end', 
'ENABLED', 1)
ON CONFLICT (name) DO NOTHING;