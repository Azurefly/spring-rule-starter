# Spring Rule Starter

A small Spring Boot + Drools rule-management baseline for applications that need rules stored in a database, hot compilation, version history, rollback, and optional multi-node refresh.

> **Security boundary:** DRL can call Java code and must be treated as trusted executable code. The management API is not an untrusted-user sandbox. See [SECURITY.md](SECURITY.md) before exposing it outside a trusted network.

## What v0.1 provides

- Dynamic Drools DRL compilation without restarting the application.
- Last-known-good in-memory container: a failed edit does not replace the active compiled rule.
- PostgreSQL-backed rule metadata and build history.
- Versioned updates and rollback from successful snapshots.
- Enable / disable / delete lifecycle operations.
- Editor-only validation that does not activate the candidate rule.
- Legacy `Order` demo execution plus generic `Map<String,Object>` fact execution.
- Startup reload of enabled rules.
- Optional Redis pub/sub for cache refresh across multiple application nodes.
- Vue 3 management console for editing, validating, executing and rolling back rules.
- CI that runs backend tests, checks JDK 17 compilation compatibility, and builds the frontend.

## Project structure

```text
spring-rule-starter/
├─ project-common       Result wrapper and common web handling
├─ project-ruleengine   Drools dependencies and simple rule-engine examples
├─ project-api          Rule persistence, compilation lifecycle, REST API
├─ project-boot         Runnable Spring Boot application and runtime config
├─ frontend             Vue 3 rule-management console
├─ schema.sql           Canonical PostgreSQL schema
└─ docker-compose.yml   Local PostgreSQL + Redis services
```

The current `0.1.0` release is a **runnable reference application and modular baseline**. A zero-boilerplate Spring Boot auto-configuration artifact suitable for publishing to Maven Central is a logical next step, but is intentionally not pretended to exist yet.

## Runtime baseline

- Java source/target: 8
- Spring Boot: 2.7.13
- Drools: 7.59.0.Final
- PostgreSQL: example uses 15
- Redis: optional, example uses 7
- Frontend: Vue 3 + Vite

## Quick start

### 1. Start PostgreSQL

The default application configuration matches `docker-compose.yml`:

```bash
docker compose up -d db
```

Redis is optional. Start it only when cluster refresh is needed:

```bash
docker compose up -d redis
```

### 2. Build and test

```bash
mvn clean verify
```

### 3. Start the backend

```bash
mvn -pl project-boot -am spring-boot:run
```

The backend listens on `http://localhost:8080` by default.

### 4. Start the frontend

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to the backend.

## Configuration

Copy `.env.example` into the configuration mechanism used by your environment. The application reads environment variables directly; it does not require a `.env` parser.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ruledb` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `ruleuser` | Database user |
| `DB_PASSWORD` | `rulepass` | Database password for local compose only |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `RULE_REDIS_ENABLED` | `false` | Enable Redis rule-refresh pub/sub |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `RULE_CORS_ALLOWED_ORIGIN_PATTERNS` | `http://localhost:5173` | Comma-separated allowed frontend origins |
| `RULE_MAX_FILE_SIZE` | `1MB` | Maximum uploaded DRL file size |

Do not commit production credentials. If a real credential has ever been committed to Git history, removing it from the current file is not sufficient: rotate the credential as well.

## Rule lifecycle

A rule has a business `version`, a lifecycle `status`, and last build information.

### Create

A new rule is compiled **before** it is persisted. Invalid DRL is rejected and no unusable rule record is created.

### Update

An update is built before the stored active content is replaced:

1. Candidate DRL compiles successfully → cache is activated, content is saved, version increments, successful history snapshot is stored.
2. Candidate DRL fails → current content/version stays unchanged, the failed candidate is recorded in history for diagnostics, and the last successful container remains active.

This prevents a bad edit from working until restart and then breaking permanently.

### Rollback

Rollback only selects a `SUCCESS` history snapshot containing rule content. The snapshot is copied into a **new** version rather than rewriting history.

### Disable / delete

Disabling or deleting a rule removes its local compiled container. With Redis refresh enabled, the same cache removal is propagated to other nodes.

## REST API

All endpoints are under `/api/rules`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Rule-engine health and loaded container count |
| `GET` | `/list` | List rules |
| `POST` | `/upload` | Upload and compile a new DRL rule |
| `POST` | `/validate?name=...` | Compile candidate DRL without activation |
| `PUT` | `/{name}` | Save content as a new successful version |
| `PATCH` | `/{name}/status?status=ENABLED|DISABLED` | Enable or disable a rule |
| `DELETE` | `/{name}` | Delete rule and history |
| `GET` | `/meta/{name}` | Rule metadata and content |
| `GET` | `/history/{name}?page=0&size=20` | Paginated build history |
| `POST` | `/rollback/{name}/{version}` | Create a new version from a successful snapshot |
| `POST` | `/refresh/{name}` | Rebuild one rule from the database |
| `POST` | `/reload-all` | Rebuild all enabled rules from the database |
| `GET` | `/loaded` | Names currently loaded in this JVM |
| `POST` | `/exec/{name}` | Legacy built-in `Order` example execution |
| `POST` | `/exec-map/{name}` | Execute with the request JSON object as a `Map` fact |

The response envelope is:

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## Example: built-in Order rule

```drl
package rules;

import com.example.ruleengine.Order;

rule "Free Shipping"
when
    $order : Order(amount > 100)
then
    $order.setFreeShipping(true);
end
```

Execute it through the compatibility endpoint:

```bash
curl -X POST http://localhost:8080/api/rules/exec/free-shipping \
  -H 'Content-Type: application/json' \
  -d '{"amount":150}'
```

## Example: generic Map fact

Rules that use the generic execution endpoint can work directly with a Java `Map`:

```drl
package rules;

import java.util.Map;

rule "VIP marker"
when
    $fact : Map( this["level"] == "VIP" )
then
    $fact.put("priority", "HIGH");
end
```

```bash
curl -X POST http://localhost:8080/api/rules/exec-map/vip-rule \
  -H 'Content-Type: application/json' \
  -d '{"level":"VIP"}'
```

## Multi-node refresh

Set the same Redis endpoint on every application node and enable:

```bash
RULE_REDIS_ENABLED=true
```

After a successful create, update, rollback, enable, disable or delete operation, the originating node publishes `rule-refresh`. Every node rebuilds or evicts only its local container. Subscribers do **not** write shared build-history rows, preventing duplicate audit records in a cluster.

Redis pub/sub is a cache-invalidation mechanism, not a durable event log. If a node misses a message, startup reload or `/reload-all` reconciles it from PostgreSQL.

## Development and CI

GitHub Actions performs:

1. `mvn clean verify` on Java 8, including rule lifecycle tests.
2. A Java 17 compile/build compatibility check.
3. `npm ci && npm run build` for the Vue frontend.

The most important regression tests cover:

- valid DRL compilation and execution;
- failed replacement preserving the last successful container;
- validation not activating a rule;
- create history containing rollback content;
- failed updates preserving current content/version;
- rollback creating a new version from a successful snapshot.

## Known scope / roadmap

The repository is intentionally small. High-value next steps are:

- Extract the dynamic compiler into a dedicated reusable core API and add a true Spring Boot auto-configuration starter artifact.
- Add an authenticated/authorized management surface instead of relying on network trust.
- Replace `ddl-auto=update` with Flyway/Liquibase migrations for production deployments.
- Add metrics for compile latency, execution count/failure, active container count, and refresh lag.
- Add richer fact-type registration / DTO binding instead of only the built-in `Order` example and generic `Map` mode.
- Add rule-set / agenda-group management and batch execution semantics.
- Define a supported upgrade line for newer Spring Boot and Drools generations without breaking Java 8 users of this baseline.

## License

Apache License 2.0. See [LICENSE](LICENSE).
