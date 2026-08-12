# Changelog

All notable changes to this project are documented in this file.

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
- Maven coordinates move to `com.azurefly:spring-rule-starter:0.1.0` while Java package names remain unchanged for source compatibility.

### Security

- Removed repository-tracked runtime database credentials from application configuration and replaced them with environment variables/local-development defaults.
- Added explicit documentation that DRL is trusted executable code and that the management API requires an external authentication/authorization boundary before exposure to untrusted users.

> If a real credential was committed before this release, removing it from the current tree does not invalidate copies in Git history. Rotate or revoke that credential.
