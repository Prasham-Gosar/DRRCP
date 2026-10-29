# Disaster Relief & Rescue Coordination Platform — Web Edition

Distributed Computing project (SPIT). One Spring Boot web app + browser dashboard that
runs all 10 DC experiments on a simulated post-disaster UAV/edge network.

| | |
|---|---|
| Stack | Java 17, Spring Boot 3.3.x (Maven), Spring Web + Data JPA, H2 file DB, SSE, static HTML/CSS/vanilla JS |
| Team | Prasham (core), Grasit (consensus + fault tolerance), Parth (compute + Spark), Vedant (comm + frontend shell) |
| Docs | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · [`docs/PROGRESS.md`](docs/PROGRESS.md) · [`docs/EXPERIMENTS_6_7.md`](docs/EXPERIMENTS_6_7.md) |
| Original console code | [`legacy/`](legacy) — kept untouched for reference |

## Run

```bash
# Optional (Exp 7 Spark): build standalone analytics-spark fat JAR (one-time or if changing Spark job):
cd analytics-spark && mvn clean package -DskipTests && cd ..

# Run web app (runs on http://localhost:8080):
mvn spring-boot:run

# Run all automated tests:
mvn test
```

H2 console: http://localhost:8080/h2-console — JDBC URL `jdbc:h2:file:./data/drs`, user `sa`, empty password.  
Live events: http://localhost:8080/api/events (SSE) · history: `/api/events/history?limit=50`

## Git rules

* One branch per task: `feature/<name>-<topic>`; never push to `main` directly — open a PR (1 review required).
* Small commits: `feat(core): add heartbeat endpoint`.
* Reviewer pairs: Prasham ↔ Grasit, Parth ↔ Vedant.
* Stay inside your own package; need something from a teammate? Ask them / list it in the PR.
