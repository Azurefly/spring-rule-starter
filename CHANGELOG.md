# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Added

- Flyway-managed schema migrations for the bundled PostgreSQL rule-management server.
- Standard Spring Boot Actuator health contribution for the in-memory `RuleEngine`.
- PostgreSQL 15 service-backed CI integration test covering Flyway migration, Hibernate schema validation and application startup.
- Optional `RuleEngineListener` runtime events in `spring-rule-core` for observability integrations without coupling the core to a metrics library.
- Optional Micrometer rule-operation timers and fired-rule distribution metrics when a `MeterRegistry` is available.
- Optional stateless API-key authentication for the bundled management server with separate `READER` and `ADMIN` authorization levels.
- Integration coverage for API-key authorization and protected Actuator metrics.

### Changed

- Bundled admin server defaults Hibernate to `ddl-auto=validate` instead of mutating schemas with `ddl-auto=update`.
- Removed the legacy unversioned `project-boot/src/main/resources/schema.sql` initialization path.
- Actuator exposes only `health,info` by default and optional Redis health probing is disabled unless explicitly enabled.
- Non-health Actuator endpoints require the `ADMIN` API key when bundled security is enabled.
- The Vue client can supply the API key from session storage and prompts only after an authentication challenge.
- Rule metric tags intentionally exclude rule names to avoid high-cardinality time series.

### Security

- `RULE_SECURITY_ENABLED=true` now requires an admin API key of at least 16 characters; startup fails closed when the security configuration is incomplete.
- Read-only keys can call `GET /api/rules/**` but cannot mutate, refresh or execute rules.
- Management API keys are compared using fixed-length SHA-256 digests with constant-time comparison.

## [0.2.0] - 2026-08-12

### Added

- `spring-rule-core`: Spring-independent public `RuleEngine` API and dynamic Drools implementation.
- `spring-rule-spring-boot-autoconfigure`: `spring.rule.*` configuration and conditional `RuleEngine` registration.
- `spring-rule-spring-boot-starter`: one-dependency integration artifact for Spring Boot applications.
- Batch fact execution with optional KIE globals through the public runtime API.
- Auto-configuration regression tests for default registration, disablement and user-bean backoff.
- Core runtime regression tests independent of the bundled management application's `Order` example.

### Changed

- Bundled admin `KieManager` now delegates dynamic compilation, execution and container lifecycle to `spring-rule-core` instead of maintaining a second Drools implementation.
- Runtime install/remove/execute lifecycle uses a read/write lock so a container cannot be disposed while an execution is actively using it.
- Maven reactor version moves to `0.2.0`.
- README now documents direct starter consumption as the primary reusable integration path.

### Compatibility

- Java source/target remains 8.
- Spring Boot baseline remains 2.7.13.
- Drools baseline remains 7.59.0.Final.
- Existing admin REST APIs and `com.example...` compatibility packages remain available.

## [0.1.0] - 2026-08-12

### Added

- DRL validation without activating the candidate rule.
- Versioned build history with content snapshots and reliable rollback.
- Rule enable, disable, delete, loaded-cache and health APIs.
- Generic `Map<String,Object>` fact execution alongside the original `Order` demo endpoint.
- Optional Redis pub/sub for multi-node cache refresh.
- Vue rule-management console with editing, validation, execution, lifecycle operations and rollback.
- Backend regression tests and frontend build validation in GitHub Actions.
- Environment-based runtime configuration, `.env.example`, `SECURITY.md` and Apache-2.0 license.

### Changed

- Invalid new rules are rejected before persistence.
- Invalid updates no longer replace the last valid database content or active Drools container.
- Reload-all now reconciles the complete in-memory cache so disabled/deleted rules cannot stay active.
- Redis subscribers no longer create duplicate shared build-history rows on every cluster node.
- Runtime logging and CORS settings are configurable and debug-only console output has been removed.
- Maven coordinates moved to `com.azurefly:*:0.1.0` while Java package names remained unchanged for source compatibility.

### Security

- Removed repository-tracked runtime database credentials from application configuration and replaced them with environment variables/local-development defaults.
- Added explicit documentation that DRL is trusted executable code and that the management API requires an external authentication/authorization boundary before exposure to untrusted users.

> If a real credential was committed before this release, removing it from the current tree does not invalidate copies in Git history. Rotate or revoke that credential.
