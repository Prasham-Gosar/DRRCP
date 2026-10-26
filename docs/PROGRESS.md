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

### Day 1 — Compute Port & Standalone Spark Module
- Ported `com.drs.compute.model`: `RescueTask`, `LoadNode` (preserving exact legacy effective capacity and utilization formulas), `NodeTrace`, `AssignmentResult`.
- Ported `com.drs.compute.balancer.LoadBalancer`: algorithmic logic identical to legacy `LoadBalancer` (lowest projected utilization wins, unavailable and zero-capacity nodes skipped, strict inequality for tie-breaking) with structured `AssignmentResult` and execution `steps`.
- Built `com.drs.compute.model.RescueTaskEntity` (JPA entity for H2 persistence) and `com.drs.compute.repository.RescueTaskRepository`.
- Built `com.drs.compute.service.LoadBalancerService` orchestrating single, batch, and entity-based task assignments, updating task status to ASSIGNED, and publishing live events via `EventPublisher.publish("compute", "TASK_ASSIGNED", result)`.
- Created standalone `analytics-spark/` Maven module (Apache Spark 3.5.3, Java 17 `--add-opens` flags configured, shade fat JAR) with `SparkAnalyticsJob` performing MapReduce on `input/rescue_tasks.txt` and writing structured JSON results (`output/analytics_result.json`) with task counts and workload per cluster and per type. Spark is kept strictly outside the Spring Boot POM.
- Added comprehensive unit tests in `LoadBalancerServiceTest` covering overloaded node skipped, unavailable node skipped, tie handling, no available node, legacy demo sequence, and event publishing, plus `RescueTaskEntityTest`.
- Decisions: (1) `LoadBalancer` retains strict `<` inequality so earlier-evaluated nodes win on ties, matching legacy demo behavior. (2) Nodes with `effectiveCapacity <= 0` (e.g. zero battery/signal) are marked as skipped with clear reason in `NodeTrace`. (3) `SparkAnalyticsJob` accepts optional command-line arguments for input and output paths to facilitate seamless subprocess invocation from Spring Boot in Day 2.

### Day 2
-

### Day 3
-

### Day 4
-

### Day 5
-

## Vedant (comm, frontend shell)

### Day 1 — Foundation & Experiment 9 Port
- Frontend design system: `static/css/app.css` with responsive layout, sidebar, cards, tables, badges (green/amber/red/blue), form controls, progress bars, toasts, and terminal step trace styling.
- Layout shell: `static/js/layout.js` injecting sidebar navigation across all 11 experiment pages, active route detection, top header with SSE status indicator, and mock mode toggle.
- API & SSE Client: `static/js/api.js` (fetch wrapper with error toast and `?mock=1` routing to `/mock/*.json`) and `static/js/events.js` (auto-reconnecting SSE client with `events.on(module, type, cb)` and reusable `createLiveLog` widget).
- Local Chart.js: Vendored Chart.js 4.x UMD into `static/js/vendor/chart.umd.min.js` (no CDN dependencies).
- Mock environment & starter template: Created `static/mock/` JSON fixtures (overview, nodes, events_history, comm_result, election_bully, reconcile, ft_state, loadbalancer) and `static/pages/_template.html` for teammates to copy.
- Main Dashboard: Built `index.html` with KPI metric cards, quick action buttons, live node telemetry table, Chart.js battery visualizer, and embedded live event stream.
- Experiment 9 port: Ported legacy `Communicator` and `CollectiveCommunicationDemo` into `com.drs.comm.service.Communicator` and `CollectiveCommService`. Replaced console prints with `steps` and `RankExecutionRecord` traces, publishing live events `COMM_BCAST`, `COMM_SCATTER`, `COMM_GATHER`.
- REST Controller: Built `CollectiveCommController` at `/api/comm/run-all`, `/bcast`, `/scatter`, `/gather`, `/status`.
- Dedicated UI: Built `static/pages/comm.html` with interactive rank grid, parameters selector, primitive execution buttons, and live step trace.
- Tests: Added 16 JUnit 5 / MockMvc tests (`CommunicatorTest`, `CollectiveCommServiceTest`, `CollectiveCommControllerTest`) — all 76 project tests passing with 0 failures.
- Decisions: Communicator keeps pure thread-based MPI simulation with `LinkedBlockingQueue` and `CyclicBarrier`. Output synchronization uses rank-ordered barrier rounds. Gather checksum verified against original packages + rank offsets.

### Day 2
-

### Day 3
-

### Day 4
-

### Day 5
-
