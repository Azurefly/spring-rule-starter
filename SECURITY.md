# Security

## DRL is trusted executable code

Drools DRL is not a safe expression language or an untrusted-user sandbox. A rule can import application classes and invoke Java methods. Treat permission to create or edit DRL as permission to execute code with the privileges of the application process.

Do not expose the rule-management endpoints directly to untrusted users or the public Internet without an authentication and authorization layer in front of them.

Recommended deployment controls include:

- restrict rule authoring to trusted administrators;
- place `/api/rules/**` behind authenticated access control or a trusted internal gateway;
- run the application with the least operating-system and database privileges it needs;
- restrict outbound network access where practical;
- review DRL changes just as you would review application code;
- keep secrets out of rule text, logs and Git history.

## Secrets

Runtime credentials belong in environment variables, a secret manager, or platform-specific secret injection. The tracked `application.yml` only contains local-development defaults.

Removing a credential from the current revision does not remove it from existing Git history. If a real credential has ever been committed, rotate or revoke it immediately. History rewriting can reduce accidental discovery but is not a substitute for rotation.

## Redis

Redis pub/sub is used only for cache-refresh notification. It is not an authorization boundary or a durable audit channel. Secure Redis according to the deployment environment and do not expose it publicly.

## Reporting a vulnerability

If GitHub private vulnerability reporting is enabled for this repository, prefer that channel for security issues. Avoid posting live credentials, exploit payloads, or sensitive deployment information in a public issue.
