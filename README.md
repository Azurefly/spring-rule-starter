# Spring Rule Starter

Spring Rule Starter provides two things in one repository:

1. a **reusable Spring Boot Starter** for dynamically compiling and executing Drools DRL at runtime; and
2. a **reference rule-management application** with PostgreSQL persistence, version history, rollback, optional Redis refresh, REST APIs and a Vue management console.

> **Security boundary:** DRL can call Java code and must be treated as trusted executable code. This project is not a sandbox for untrusted rule authors. See [SECURITY.md](SECURITY.md).

## v0.2 highlights

- New Spring-independent `spring-rule-core` runtime API.
- New `spring-rule-spring-boot-autoconfigure` module.
- New one-dependency `spring-rule-spring-boot-starter` module.
- The bundled admin application now delegates to the same public `RuleEngine` implementation used by starter consumers.
- Failed rule replacement preserves the last-known-good container.
- Rule execution and hot replacement are protected by a read/write lifecycle lock so an active container is not disposed while a request is using it.
- Starter auto-configuration backs off when an application supplies its own `RuleEngine`.
- The bundled admin server uses Flyway migrations and Hibernate schema validation rather than implicit schema mutation.
- The in-memory rule runtime contributes to standard Spring Boot Actuator health.
- Existing v0.1 rule persistence, history, rollback, lifecycle, Redis refresh and Vue management features remain available.

## Project structure

```text
spring-rule-starter/
├─ spring-rule-core
│  └─ Spring-independent public RuleEngine API + Drools implementation
├─ spring-rule-spring-boot-autoconfigure
│  └─ spring.rule.* properties and RuleEngine auto-configuration
├─ spring-rule-spring-boot-starter
│  └─ Dependency starter consumed by business applications
├─ project-common
│  └─ Common response/web support used by the bundled admin application
├─ project-ruleengine
│  └─ Legacy examples / Order demo kept for compatibility
├─ project-api
│  └─ Persistence, rule lifecycle, version history and admin REST API
├─ project-boot
│  ├─ Runnable reference management server
│  └─ src/main/resources/db/migration/  versioned Flyway schema migrations
├─ frontend
│  └─ Vue 3 rule-management console
├─ schema.sql
└─ docker-compose.yml
```

The architectural boundary in v0.2 is intentional:

```text
Business application
      │
      ▼
spring-rule-spring-boot-starter
      │
      ▼
spring-rule-spring-boot-autoconfigure
      │
      ▼
spring-rule-core  ◄──────── project-api / KieManager
      │
      ▼
Drools / KIE
```

The admin application no longer owns a second dynamic Drools compiler. `KieManager` is now an adapter for database loading and build-status semantics around the public `RuleEngine`.

## Runtime baseline

- Java source/target: 8
- Spring Boot: 2.7.13
- Drools: 7.59.0.Final
- PostgreSQL: local and CI baseline uses 15
- Flyway: managed by Spring Boot 2.7 dependency management
- Redis: optional, local example uses 7
- Frontend: Vue 3 + Vite

## Use it as a Spring Boot Starter

The artifacts are currently built from this GitHub repository; Maven Central publication is not claimed yet. For local development, first install the repository:

```bash
mvn clean install
```

Then add one dependency to another Spring Boot 2.7 application:

```xml
<dependency>
  <groupId>com.azurefly</groupId>
  <artifactId>spring-rule-spring-boot-starter</artifactId>
  <version>0.2.0</version>
</dependency>
```

A `RuleEngine` bean is created automatically:

```java
import com.azurefly.rule.core.RuleBuildResult;
import com.azurefly.rule.core.RuleEngine;
import org.springframework.stereotype.Service;

@Service
public class PricingRuleService {
    private final RuleEngine ruleEngine;

    public PricingRuleService(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public void load(String name, String drl) {
        RuleBuildResult result = ruleEngine.install(name, drl);
        if (!result.isSuccess()) {
            throw new IllegalArgumentException(result.getMessage());
        }
    }

    public int execute(String name, Object fact) {
        return ruleEngine.execute(name, fact);
    }
}
```

### Public `RuleEngine` API

```java
RuleBuildResult validate(String ruleName, String drl);
RuleBuildResult install(String ruleName, String drl);
int execute(String ruleName, Object fact);
int execute(String ruleName, Iterable<?> facts, Map<String,Object> globals);
boolean contains(String ruleName);
Set<String> getLoadedRuleNames();
void remove(String ruleName);
void clear();
```

`validate` compiles without activation. `install` compiles first and only swaps the active container after a successful build. If the candidate is invalid, the existing active rule remains available.

### Starter configuration

```yaml
spring:
  rule:
    enabled: true
    release-group-id: com.mycompany.rules
    version-prefix: 1.0
```

| Property | Default | Description |
|---|---|---|
| `spring.rule.enabled` | `true` | Enable `RuleEngine` auto-configuration |
| `spring.rule.release-group-id` | `com.azurefly.rules` | GroupId used for generated in-memory KIE modules |
| `spring.rule.version-prefix` | `1.0` | Prefix used for generated dynamic module versions |

Set `spring.rule.enabled=false` to disable the starter. If the application declares its own `RuleEngine` bean, the built-in auto-configuration backs off automatically.

### Batch facts and globals

The public runtime is not limited to the bundled `Order` example:

```java
int fired = ruleEngine.execute(
    "pricing",
    facts,
    Collections.<String,Object>singletonMap("clock", clock)
);
```

Every invocation uses a fresh `KieSession` and disposes it after execution.

## Run the bundled management application

### 1. Start PostgreSQL

```bash
docker compose up -d db
```

Redis is optional:

```bash
docker compose up -d redis
```

### 2. Build and test

```bash
mvn clean verify
```

### 3. Start backend

```bash
mvn -pl project-boot -am spring-boot:run
```

Backend default: `http://localhost:8080`.

On startup, Flyway applies pending migrations from `project-boot/src/main/resources/db/migration`. Hibernate then runs in `validate` mode by default; it no longer changes the schema itself.

### 4. Start frontend

```bash
cd frontend
npm ci
npm run dev
```

Frontend default: `http://localhost:5173`.

## Management application configuration

The reference server reads environment variables directly; `.env` parsing is not required.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ruledb` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `ruleuser` | Database user |
| `DB_PASSWORD` | `rulepass` | Local compose password |
| `FLYWAY_ENABLED` | `true` | Apply versioned database migrations on startup |
| `JPA_DDL_AUTO` | `validate` | Hibernate schema action; keep `validate` for managed environments |
| `JPA_SHOW_SQL` | `false` | Enable Hibernate SQL logging |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `RULE_REDIS_ENABLED` | `false` | Enable Redis rule-refresh pub/sub |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `RULE_CORS_ALLOWED_ORIGIN_PATTERNS` | `http://localhost:5173` | Allowed frontend origins |
| `RULE_MAX_FILE_SIZE` | `1MB` | Maximum uploaded DRL size |
| `MANAGEMENT_ENDPOINTS` | `health,info` | Actuator endpoints exposed over HTTP |
| `MANAGEMENT_HEALTH_SHOW_DETAILS` | `never` | Actuator health detail exposure |
| `MANAGEMENT_REDIS_HEALTH_ENABLED` | `false` | Enable Redis health contributor when Redis is intentionally required |

Do not commit production credentials. If a real credential was previously committed, rotate it; deleting it from the current tree does not erase Git history.

## Database migrations

The bundled management server treats Flyway as the owner of schema evolution.

- `V1__baseline_rule_schema.sql` creates/adopts the v0.1/v0.2 rule tables.
- `baseline-on-migrate=true` with baseline version `0` lets an existing development schema enter Flyway management before V1 is applied.
- Hibernate defaults to `ddl-auto=validate`, so an entity/schema mismatch fails startup instead of silently changing the database.
- New schema changes should be added as `V2__...sql`, `V3__...sql`, and so on. Do not edit a migration after it has been released to shared environments.
- Flyway `clean` is disabled in application configuration.

The root-level `schema.sql` is retained as a human-readable/reference schema; `project-boot` no longer uses an unversioned `schema.sql` startup initializer.

## Health checks

The admin server uses Spring Boot Actuator. By default only `health` and `info` are exposed.

```text
GET /actuator/health
```

The `ruleEngine` health contributor reports the in-memory runtime status and loaded-rule count. Health details are hidden by default. Redis health probing is disabled by default because Redis refresh is an optional capability; enable it only when Redis is part of the deployment's required availability boundary.

The existing `/api/rules/health` endpoint remains available for rule-management-specific information, but deployment systems should prefer the standard Actuator health endpoint.

## Rule lifecycle in the admin application

### Create

A new DRL is compiled before persistence. Invalid DRL is rejected and no unusable active rule is created.

### Update

1. Candidate compiles successfully → public `RuleEngine` activates it, DB content is updated, version increments, successful history snapshot is recorded.
2. Candidate fails → current DB content/version stays unchanged, failed candidate is retained in history for diagnostics, and the last-known-good runtime container remains active.

### Rollback

Rollback selects a successful history snapshot containing DRL content and creates a new version from it. Existing history is never rewritten.

### Disable / delete

The local `RuleEngine` container is removed. When Redis refresh is enabled, other nodes receive the same invalidation signal.

## REST API

All management endpoints are under `/api/rules`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Rule-engine health and loaded count |
| `GET` | `/list` | List rules |
| `POST` | `/upload` | Upload and compile a new rule |
| `POST` | `/validate?name=...` | Validate DRL without activation |
| `PUT` | `/{name}` | Save a new successful version |
| `PATCH` | `/{name}/status?status=ENABLED|DISABLED` | Enable/disable |
| `DELETE` | `/{name}` | Delete rule/history |
| `GET` | `/meta/{name}` | Rule metadata/content |
| `GET` | `/history/{name}?page=0&size=20` | Build history |
| `POST` | `/rollback/{name}/{version}` | Roll back via a new version |
| `POST` | `/refresh/{name}` | Rebuild one DB rule |
| `POST` | `/reload-all` | Reconcile all enabled DB rules |
| `GET` | `/loaded` | Rules loaded in this JVM |
| `POST` | `/exec/{name}` | Compatibility `Order` example |
| `POST` | `/exec-map/{name}` | Execute request JSON as a Map fact |

## Multi-node refresh

Enable Redis on every admin-server node:

```bash
RULE_REDIS_ENABLED=true
```

The database remains the source of truth. Redis pub/sub is used only for local runtime cache invalidation/reload and is not treated as a durable event log.

## Development and CI

GitHub Actions verifies:

1. `mvn clean verify` on Java 8 against a real PostgreSQL 15 service, including Flyway migration, Hibernate schema validation, `spring-rule-core`, auto-configuration and management lifecycle tests.
2. Java 17 build compatibility.
3. `npm ci && npm run build` for the Vue frontend.

Key regression guarantees include:

- public core runtime compiles and executes a standalone fact type;
- validation does not activate a rule;
- invalid replacement keeps the last-known-good container;
- starter auto-configures by default, can be disabled, and backs off for a user bean;
- Flyway creates/adopts the expected PostgreSQL schema and records migration V1;
- the Spring Boot management application starts with Hibernate schema validation enabled;
- the `RuleEngine` Actuator health contributor reports `UP` during a healthy application context;
- management create history stores rollback content;
- failed management updates preserve active content/version;
- rollback creates a new version from a successful snapshot.

## Roadmap

High-value next steps after v0.2:

- authenticated/authorized management APIs;
- Micrometer compile/execution/refresh metrics;
- rule namespaces / rule sets / agenda-group management;
- fact type adapters and JSON-to-DTO binding;
- Maven Central publishing and signed release automation;
- a separate compatibility line for Spring Boot 3 / current Drools while preserving this Java 8 baseline.

## License

Apache License 2.0. See [LICENSE](LICENSE).
