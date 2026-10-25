# Progress Log

One section per person per day. Update yours in every PR.

## Prasham (core, repo/infra)

### Day 1 — Foundation
- Repo, branch protection, collaborators, PR template, `.gitignore`, original code under `/legacy`.
- Maven Spring Boot 3.3 skeleton, package-info in every package, H2 file DB, H2 console on port 8080.
- Event bus: `EventPublisher`, `EventLog`, SSE at `/api/events`, `/api/events/history?limit=`.
- Ported `NodeStatus` (JPA, Serializable) and RMI `NodeService`; `CoordinatorService` (register/score, heartbeat, silent-node monitor, getTime) + REST controller.
- JUnit tests for model, service, event bus and controller.
- `docs/ARCHITECTURE.md` and this file.
- Decisions: silence alert is published once per silence episode (legacy printed every second); battery/signal validated 0-100 at the REST boundary.

### Day 2
-

### Day 3
-

### Day 4
-

### Day 5
-

## Grasit (consensus, fault tolerance)

### Day 1
- Ported `com.drs.consensus.model`: `ElectionNode`, `VectorClock`, `UpdateRecord`, `ElectionResult`, `Resolution`, `ReconciliationResult`.
- Ported `com.drs.consensus.election`: `BullyElectionService`, `RingElectionService` — algorithm logic identical to legacy `BullyElection`/`RingElection`, all `System.out` replaced with `List<String> steps` in `ElectionResult` + live event via `EventPublisher.publish("consensus", "LEADER_ELECTED", ...)`.
- Ported `com.drs.consensus.reconcile`: `VectorClockRegistry` (one VectorClock per node, thread-safe), `ReconciliationService` (causal frontier, safety-first status priority, CRDT set union — identical to legacy `ReconciliationEngine`). Pending updates stored in `CopyOnWriteArrayList`. All results published via `EventPublisher.publish("consensus", "RECONCILED", ...)`.
- Ported `com.drs.faulttolerance`: `SystemUpdate`, `FaultToleranceResult`, `FaultToleranceService` (primary-backup replication, fail, auto-promote, post-failover processing, reset — identical to legacy `PrimaryCoordinator` + `BackupReplica`). Events: `FT_REPLICATED`, `FT_PRIMARY_FAILED`, `FT_BACKUP_PROMOTED`.
- JUnit tests: 36 tests, 0 failures — `BullyElectionServiceTest` (7), `RingElectionServiceTest` (6), `VectorClockTest` (10), `ReconciliationServiceTest` (7), `FaultToleranceServiceTest` (6).
- Added Section 8 (DTO definitions) to `docs/ARCHITECTURE.md`.
- Decisions: (1) `FaultToleranceService` auto-promotes backup on `failPrimary()` (legacy demo always promoted immediately after). (2) `Resolution.contributingUpdates` is `List<Map<String,Object>>` for clean JSON serialisation instead of embedding full `UpdateRecord` objects. (3) `VectorClockRegistry` uses `ConcurrentHashMap` for thread safety — Prasham's core can use it for Exp 3 clock sync.


### Day 2
-

### Day 3
-

### Day 4
-

### Day 5
-

## Parth (compute, Spark)

### Day 1
-

### Day 2
-

### Day 3
-

### Day 4
-

### Day 5
-

## Vedant (comm, frontend shell)

### Day 1
-

### Day 2
-

### Day 3
-

### Day 4
-

### Day 5
-
