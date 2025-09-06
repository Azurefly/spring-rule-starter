
CREATE TABLE IF NOT EXISTS rule_meta (
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
