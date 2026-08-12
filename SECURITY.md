# Security

## DRL is trusted executable code

Drools DRL is not a safe expression language or an untrusted-user sandbox. A rule can import application classes and invoke Java methods. Treat permission to create or edit DRL as permission to execute code with the privileges of the application process.

Do not expose the rule-management endpoints directly to untrusted users or the public Internet without authentication, authorization, TLS and appropriate network controls.

Recommended deployment controls include:

- restrict rule authoring to trusted administrators;
- enable the bundled API-key security or place `/api/rules/**` behind a trusted identity-aware gateway;
- terminate TLS before requests reach the management API;
- run the application with the least operating-system and database privileges it needs;
- restrict outbound network access where practical;
- review DRL changes just as you would review application code;
- keep secrets out of rule text, logs and Git history.

## Bundled API-key security

The reference management server includes optional stateless API-key authentication. It is disabled by default to preserve local-development compatibility. Shared or controlled deployments should enable it explicitly:

```text
RULE_SECURITY_ENABLED=true
RULE_ADMIN_API_KEY=<random secret of at least 16 characters>
RULE_READER_API_KEY=<optional separate read-only secret>
```

The default request header is `X-Rule-Api-Key` and can be changed with `RULE_SECURITY_HEADER`.

Authorization rules when enabled:

- `GET /api/rules/**`: `READER` or `ADMIN` key;
- non-GET `/api/rules/**`: `ADMIN` key only;
- `/actuator/health` and `/actuator/info`: public for deployment probes;
- other exposed `/actuator/**` endpoints: `ADMIN` key only.

The admin key also has reader permissions. The reader key is optional. Enabling security without a valid admin key causes startup to fail rather than silently running unsecured.

API keys are bearer secrets, not user identities. For environments needing per-user identity, MFA, centralized revocation, SSO or detailed audit attribution, use an upstream identity-aware gateway or replace this reference security boundary with the organization's standard authentication system.

## Secrets

Runtime credentials and API keys belong in environment variables, a secret manager, or platform-specific secret injection. The tracked `application.yml` only contains local-development defaults/placeholders.

Removing a credential from the current revision does not remove it from existing Git history. If a real credential has ever been committed, rotate or revoke it immediately. History rewriting can reduce accidental discovery but is not a substitute for rotation.

## Observability

Rule runtime metrics intentionally avoid rule names as metric tags to prevent unbounded/high-cardinality series. Metrics can still reveal operational behavior; do not expose Actuator metrics publicly. With bundled API-key security enabled, non-health Actuator endpoints require the admin key.

## Redis

Redis pub/sub is used only for cache-refresh notification. It is not an authorization boundary or a durable audit channel. Secure Redis according to the deployment environment and do not expose it publicly.

## Reporting a vulnerability

If GitHub private vulnerability reporting is enabled for this repository, prefer that channel for security issues. Avoid posting live credentials, exploit payloads, or sensitive deployment information in a public issue.
