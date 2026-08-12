-- Baseline schema for the bundled rule-management server.
-- The statements are intentionally tolerant of the v0.1/v0.2 development schema so
-- an existing local database can be adopted with Flyway baselineVersion=0.

CREATE TABLE IF NOT EXISTS rule_meta (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    type VARCHAR(32) NOT NULL DEFAULT 'DROOLS',
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    version INTEGER NOT NULL DEFAULT 1,
    last_build_at TIMESTAMP,
    last_build_status VARCHAR(20),
    last_build_message TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS type VARCHAR(32) NOT NULL DEFAULT 'DROOLS';
ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ENABLED';
ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS last_build_at TIMESTAMP;
ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS last_build_status VARCHAR(20);
ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS last_build_message TEXT;
ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE rule_meta ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS rule_build_history (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(120) NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    message TEXT,
    content TEXT,
    built_by VARCHAR(100),
    built_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE rule_build_history ADD COLUMN IF NOT EXISTS version INTEGER;
ALTER TABLE rule_build_history ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE rule_build_history ADD COLUMN IF NOT EXISTS message TEXT;
ALTER TABLE rule_build_history ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE rule_build_history ADD COLUMN IF NOT EXISTS built_by VARCHAR(100);
ALTER TABLE rule_build_history ADD COLUMN IF NOT EXISTS built_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Flyway executes this migration once. Known v0.1/v0.2 schemas do not contain this
-- named constraint; older root schema variants may contain an equivalent unnamed FK.
-- A plain ALTER avoids Flyway 8/PostgreSQL JDBC parsing problems with DO $$ blocks.
ALTER TABLE rule_build_history
    ADD CONSTRAINT fk_rule_history_rule
    FOREIGN KEY (rule_name) REFERENCES rule_meta(name) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_rule_meta_status ON rule_meta(status);
CREATE INDEX IF NOT EXISTS idx_rule_meta_updated_at ON rule_meta(updated_at);
CREATE INDEX IF NOT EXISTS idx_rule_build_history_rule_name ON rule_build_history(rule_name);
CREATE INDEX IF NOT EXISTS idx_rule_build_history_built_at ON rule_build_history(built_at);
