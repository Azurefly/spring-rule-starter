
# Spring Rule Starter

Multi-module Maven skeleton for a Spring Boot based rule engine starter.
Based on a Drools + custom-rule approach.

Modules:
- project-common: common utilities (Result, Exception handler)
- project-ruleengine: rule engine core (Drools helper, simple engine)
- project-api: REST controllers, services, JPA entities
- project-boot: Spring Boot application

Quick start (development):
1. Ensure JDK 8 (Java 1.8) and Maven installed.
2. Start DB/Redis using docker-compose:
   `docker compose up -d`
3. Build:
   `mvn -DskipTests clean install`
4. Run:
   `cd project-boot && mvn spring-boot:run`


## Frontend (dev)

To run the simple Vue frontend:

```
cd frontend
npm install
npm run dev
```

The dev server runs on port 5173 by default. The backend CORS allows requests from http://localhost:5173.


Rollback: POST /api/rules/rollback/{name}/{version} will rollback the rule to the specified historical version (creates a new version and rebuilds).
