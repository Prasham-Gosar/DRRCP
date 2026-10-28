# Progress Log

One section per person per day. Update yours in every PR.

## Prasham (core, repo/infra)

### Day 1 Ã¢â‚¬â€ Foundation
- Repo, branch protection, collaborators, PR template, `.gitignore`, original code under `/legacy`.
- Maven Spring Boot 3.3 skeleton, package-info in every package, H2 file DB, H2 console on port 8080.
- Event bus: `EventPublisher`, `EventLog`, SSE at `/api/events`, `/api/events/history?limit=`.
- Ported `NodeStatus` (JPA, Serializable) and RMI `NodeService`; `CoordinatorService` (register/score, heartbeat, silent-node monitor, getTime) + REST controller.
- JUnit tests for model, service, event bus and controller.
- `docs/ARCHITECTURE.md` and this file.
- Decisions: silence alert is published once per silence episode (legacy printed every second); battery/signal validated 0-100 at the REST boundary.

### Day 2
- Built `ElectionController` with `POST /api/election/bully`, `POST /api/election/ring`, and `POST /api/election/compare` accepting `{ nodes: [...], initiatorId, clusterId }` and returning structured `ElectionResult` with leader, message count, and step traces.
- Fallback logic for default nodes: queries Prasham's nodes in `Cluster-A` via `NodeEntityRepository` and gracefully falls back to default hardcoded nodes (`UAV-01`, `UAV-02`, `Edge-01`, `Edge-02`, `Base-01`) if not present.
- Created `ElectionRecord` JPA entity with repository query `findTopNByOrderByTimestampDesc`, exposed at `GET /api/election/history`.
- Published `ELECTION_STARTED` and `LEADER_ELECTED` SSE events via `EventPublisher`.
- Defined interface `LeaderProvider` with `currentLeader(clusterId)` and implemented it on `ElectionController` for Parth's `LoadBalancer`.
- Built dedicated UI `static/pages/election.html` and modular script `static/js/election.js` featuring interactive node cards with priority, alive/dead toggles, initiator dropdown, execution controls, step-by-step pulse animation, Chart.js comparison bar chart, election history table, and live SSE event feed.
- Ported legacy Experiment 8: migrated `SystemUpdate`, `PrimaryCoordinator`, and `BackupReplica` into `com.drs.faulttolerance.model`, replacing all console outputs with structured return objects and events.
- Created `ReplicationService` holding one `PrimaryCoordinator` and one `BackupReplica` for synchronous replication, failover promotion, and post-takeover updates. Backwards-compatible `FaultToleranceService` extends `ReplicationService`.
- Added unit tests: `ElectionControllerTest` (8 tests) and `ReplicationServiceTest` (4 tests) covering replication, failover, promotion, and model operations. All 109 project tests passing.

### Day 3
-

### Day 4
-

### Day 5
-

## Grasit (consensus, fault tolerance)

### Day 1
- Ported `com.drs.consensus.model`: `ElectionNode`, `VectorClock`, `UpdateRecord`, `ElectionResult`, `Resolution`, `ReconciliationResult`.
- Ported `com.drs.consensus.election`: `BullyElectionService`, `RingElectionService` Ã¢â‚¬â€ algorithm logic identical to legacy `BullyElection`/`RingElection`, all `System.out` replaced with `List<String> steps` in `ElectionResult` + live event via `EventPublisher.publish("consensus", "LEADER_ELECTED", ...)`.
- Ported `com.drs.consensus.reconcile`: `VectorClockRegistry` (one VectorClock per node, thread-safe), `ReconciliationService` (causal frontier, safety-first status priority, CRDT set union Ã¢â‚¬â€ identical to legacy `ReconciliationEngine`). Pending updates stored in `CopyOnWriteArrayList`. All results published via `EventPublisher.publish("consensus", "RECONCILED", ...)`.
- Ported `com.drs.faulttolerance`: `SystemUpdate`, `FaultToleranceResult`, `FaultToleranceService` (primary-backup replication, fail, auto-promote, post-failover processing, reset Ã¢â‚¬â€ identical to legacy `PrimaryCoordinator` + `BackupReplica`). Events: `FT_REPLICATED`, `FT_PRIMARY_FAILED`, `FT_BACKUP_PROMOTED`.
- JUnit tests: 36 tests, 0 failures Ã¢â‚¬â€ `BullyElectionServiceTest` (7), `RingElectionServiceTest` (6), `VectorClockTest` (10), `ReconciliationServiceTest` (7), `FaultToleranceServiceTest` (6).
- Added Section 8 (DTO definitions) to `docs/ARCHITECTURE.md`.
- Decisions: (1) `FaultToleranceService` auto-promotes backup on `failPrimary()` (legacy demo always promoted immediately after). (2) `Resolution.contributingUpdates` is `List<Map<String,Object>>` for clean JSON serialisation instead of embedding full `UpdateRecord` objects. (3) `VectorClockRegistry` uses `ConcurrentHashMap` for thread safety Ã¢â‚¬â€ Prasham's core can use it for Exp 3 clock sync.


### Day 2
- Built `ElectionController` with `POST /api/election/bully`, `POST /api/election/ring`, and `POST /api/election/compare` accepting `{ nodes: [...], initiatorId, clusterId }` and returning structured `ElectionResult` with leader, message count, and step traces.
- Fallback logic for default nodes: queries Prasham's nodes in `Cluster-A` via `NodeEntityRepository` and gracefully falls back to default hardcoded nodes (`UAV-01`, `UAV-02`, `Edge-01`, `Edge-02`, `Base-01`) if not present.
- Created `ElectionRecord` JPA entity with repository query `findTopNByOrderByTimestampDesc`, exposed at `GET /api/election/history`.
- Published `ELECTION_STARTED` and `LEADER_ELECTED` SSE events via `EventPublisher`.
- Defined interface `LeaderProvider` with `currentLeader(clusterId)` and implemented it on `ElectionController` for Parth's `LoadBalancer`.
- Built dedicated UI `static/pages/election.html` and modular script `static/js/election.js` featuring interactive node cards with priority, alive/dead toggles, initiator dropdown, execution controls, step-by-step pulse animation, Chart.js comparison bar chart, election history table, and live SSE event feed.
- Ported legacy Experiment 8: migrated `SystemUpdate`, `PrimaryCoordinator`, and `BackupReplica` into `com.drs.faulttolerance.model`, replacing all console outputs with structured return objects and events.
- Created `ReplicationService` holding one `PrimaryCoordinator` and one `BackupReplica` for synchronous replication, failover promotion, and post-takeover updates. Backwards-compatible `FaultToleranceService` extends `ReplicationService`.
- Added unit tests: `ElectionControllerTest` (8 tests) and `ReplicationServiceTest` (4 tests) covering replication, failover, promotion, and model operations. All 109 project tests passing.

### Day 3
-

### Day 4
-

### Day 5
-

## Parth (compute, Spark)

### Day 1 Ã¢â‚¬â€ Compute Port & Standalone Spark Module
- Ported `com.drs.compute.model`: `RescueTask`, `LoadNode` (preserving exact legacy effective capacity and utilization formulas), `NodeTrace`, `AssignmentResult`.
- Ported `com.drs.compute.balancer.LoadBalancer`: algorithmic logic identical to legacy `LoadBalancer` (lowest projected utilization wins, unavailable and zero-capacity nodes skipped, strict inequality for tie-breaking) with structured `AssignmentResult` and execution `steps`.
- Built `com.drs.compute.model.RescueTaskEntity` (JPA entity for H2 persistence) and `com.drs.compute.repository.RescueTaskRepository`.
- Built `com.drs.compute.service.LoadBalancerService` orchestrating single, batch, and entity-based task assignments, updating task status to ASSIGNED, and publishing live events via `EventPublisher.publish("compute", "TASK_ASSIGNED", result)`.
- Created standalone `analytics-spark/` Maven module (Apache Spark 3.5.3, Java 17 `--add-opens` flags configured, shade fat JAR) with `SparkAnalyticsJob` performing MapReduce on `input/rescue_tasks.txt` and writing structured JSON results (`output/analytics_result.json`) with task counts and workload per cluster and per type. Spark is kept strictly outside the Spring Boot POM.
- Added comprehensive unit tests in `LoadBalancerServiceTest` covering overloaded node skipped, unavailable node skipped, tie handling, no available node, legacy demo sequence, and event publishing, plus `RescueTaskEntityTest`.
- Decisions: (1) `LoadBalancer` retains strict `<` inequality so earlier-evaluated nodes win on ties, matching legacy demo behavior. (2) Nodes with `effectiveCapacity <= 0` (e.g. zero battery/signal) are marked as skipped with clear reason in `NodeTrace`. (3) `SparkAnalyticsJob` accepts optional command-line arguments for input and output paths to facilitate seamless subprocess invocation from Spring Boot in Day 2.

### Day 2 — Exp 6 Live: Task API, Load Balancer & Interactive Dashboard
- Built `TaskController` (`POST /api/tasks`, `GET /api/tasks`, `POST /api/tasks/{id}/assign`, `POST /api/tasks/seed`, `DELETE /api/tasks`) and `LoadBalancerController` (`GET /api/loadbalancer/nodes`, `POST /api/loadbalancer/assign-all`, `POST /api/loadbalancer/nodes/{id}/available`, `POST /api/loadbalancer/nodes/{id}/toggle`, `POST /api/loadbalancer/reset`).
- Enhanced `LoadBalancerService` with live candidate node management: dynamically initializes from Prasham's `NodeEntityRepository` (or falls back to stubs `UAV-01`, `UAV-02`, `Edge-01`, `Edge-02`), maintains in-memory loads and availability, and supports sequential batch assignment (`assignAllPending`).
- Live SSE Event Integration: Emits `compute` events `TASK_CREATED` (with saved task entity payload) and `TASK_ASSIGNED` (with complete `AssignmentResult` payload and evaluation traces) via `EventPublisher`.
- Frontend Dashboard: Built `static/load-balancer.html`, `static/pages/loadbalancer.html`, and `static/js/load-balancer.js`:
  - Interactive Task Creation form (description, workload, category, target cluster, optional custom ID).
  - Real-time candidate fleet cards displaying battery, signal, max capacity, effective capacity, current load, and animated horizontal utilization bars (color-coded green <50%, amber 50-80%, red >80%).
  - 'Mark node unavailable' toggle on each node card enabling live node failure simulation and dynamic re-balancing demonstrations.
  - Expandable Decision Inspector with monospace execution step log and candidate evaluation breakdown table showing projected utilization and outcome per node.
  - Dedicated Pending and Assigned task tables with individual 'Assign Now' and 'Inspect Trace' actions.
  - 'Load Sample Tasks' button seeding legacy disaster relief tasks (Search, Medical, Food) into the queue.
  - Real-time SSE compute event feed and offline mock mode compatibility (`?mock=1`).
- Tests: Added comprehensive unit and MockMvc tests in `LoadBalancerServiceTest`, `LoadBalancerControllerTest`, and `TaskControllerTest` covering single task assignment, batch assign-all, tie-breaking, failover re-balancing, and repository entity updates.
- Decisions:
  1. Default fallback nodes (`UAV-01`, `UAV-02`, `Edge-01`, `Edge-02`) ensure the system is immediately testable before Prasham's node registration endpoint is fully seeded.
  2. Sequential assignment in `assignAllPending` ensures subsequent tasks see projected loads of earlier tasks in the batch, exactly matching legacy batch behavior.
  3. Created both `static/load-balancer.html` and `static/pages/loadbalancer.html` so existing sidebar navigation and direct URLs function seamlessly without editing Vedant's `layout.js`.
- Needs from Prasham:
  - `NodeEntityRepository.findAll()` / `findByClusterId(String clusterId)` to populate candidate fleet dynamically from live registrations. Safe fallback to default stubs is currently active.
- Note for Vedant:
  - You can optionally add `aliases: ['/load-balancer.html', '/pages/loadbalancer.html']` to the `loadbalancer` entry in `static/js/layout.js`. Both paths work out of the box.
### Day 3
-

### Day 4
-

### Day 5
-

## Vedant (comm, frontend shell)

### Day 1 Ã¢â‚¬â€ Foundation & Experiment 9 Port
- Frontend design system: `static/css/app.css` with responsive layout, sidebar, cards, tables, badges (green/amber/red/blue), form controls, progress bars, toasts, and terminal step trace styling.
- Layout shell: `static/js/layout.js` injecting sidebar navigation across all 11 experiment pages, active route detection, top header with SSE status indicator, and mock mode toggle.
- API & SSE Client: `static/js/api.js` (fetch wrapper with error toast and `?mock=1` routing to `/mock/*.json`) and `static/js/events.js` (auto-reconnecting SSE client with `events.on(module, type, cb)` and reusable `createLiveLog` widget).
- Local Chart.js: Vendored Chart.js 4.x UMD into `static/js/vendor/chart.umd.min.js` (no CDN dependencies).
- Mock environment & starter template: Created `static/mock/` JSON fixtures (overview, nodes, events_history, comm_result, election_bully, reconcile, ft_state, loadbalancer) and `static/pages/_template.html` for teammates to copy.
- Main Dashboard: Built `index.html` with KPI metric cards, quick action buttons, live node telemetry table, Chart.js battery visualizer, and embedded live event stream.
- Experiment 9 port: Ported legacy `Communicator` and `CollectiveCommunicationDemo` into `com.drs.comm.service.Communicator` and `CollectiveCommService`. Replaced console prints with `steps` and `RankExecutionRecord` traces, publishing live events `COMM_BCAST`, `COMM_SCATTER`, `COMM_GATHER`.
- REST Controller: Built `CollectiveCommController` at `/api/comm/run-all`, `/bcast`, `/scatter`, `/gather`, `/status`.
- Dedicated UI: Built `static/pages/comm.html` with interactive rank grid, parameters selector, primitive execution buttons, and live step trace.
- Tests: Added 16 JUnit 5 / MockMvc tests (`CommunicatorTest`, `CollectiveCommServiceTest`, `CollectiveCommControllerTest`) Ã¢â‚¬â€ all 76 project tests passing with 0 failures.
- Decisions: Communicator keeps pure thread-based MPI simulation with `LinkedBlockingQueue` and `CyclicBarrier`. Output synchronization uses rank-ordered barrier rounds. Gather checksum verified against original packages + rank offsets.

### Day 2 â€” Experiment 9 Live: Collective Communication Page & Shell Fixes
- Backend refactoring: Built `CommService` and `CommController` (with `CollectiveCommService` backward compatibility stub) providing REST endpoints POST `/api/comm/bcast`, `/scatter`, `/gather`, `/run-all` with request body `{ processes, chunkSize, alertText, zoneIds }`.
- Disaster flavour: Root rank 0 operates as the "Command Centre" broadcasting an emergency alert text; scatter splits disaster zone IDs across rescue teams; teams perform local search and rescue; gather collects verified mission reports back into Command Centre in strict ascending rank order.
- Structured response: Returns `per-rank timeline` (`rank`, `action`, `data`, `order`), `scattered` zone array, `gathered` reports array, `selfCheckPassed` boolean, and barrier-synchronized execution `steps`.
- Input validation: Strict boundary checks for processes (2..16) and chunkSize (1..10) returning HTTP 400 Bad Request with descriptive JSON error payload.
- SSE events: Published live events `COMM_BCAST`, `COMM_SCATTER`, `COMM_GATHER` through `EventPublisher`.
- UI & Canvas: Built `static/pages/comm.html`, `static/comm.html`, and modular `static/js/comm.js`:
  - Rank boxes arranged in a horizontal row with distinct Command Centre (Root) accent and worker rescue team status pills.
  - Animated SVG flow canvas with dynamic bezier arrows and directional pulses for Bcast (outward burst), Scatter (discrete zone dispatch), and Gather (inward assembly).
  - Prominent Self-Check Badge (`SELF-CHECK: PASSED` in green glow) with assembled buffer inspector.
  - Interactive per-rank timeline table and monospace terminal trace.
  - Interactive controls for processes (2..16), chunk size (1..10), root rank, and alert preset cycle.
  - Live SSE feed filtered for module `comm`.
  - Offline mock mode fallback (`?mock=1`).
- Shell fixes:
  - Fixed navigation active state in `layout.js` by adding alias mapping for both `/comm.html` and `/pages/comm.html`.
  - Added horizontal scroll overflow support in `app.css` for up to 16 rank units.
- Unit & Integration Tests: Added comprehensive tests in `CommServiceTest` and `CommControllerTest` verifying full workflow, self-check verification, boundary validations, and MockMvc HTTP responses.
- Peer Review of Parth's PR #5 (`compute` & `analytics-spark`):
  - Reviewed `LoadBalancer.java`, `LoadBalancerService.java`, `RescueTaskEntity.java`, and `SparkAnalyticsJob.java`.
  - Logic correctly preserves legacy effective capacity calculations, strict `<` inequality tie-breaking, and exclusion of zero/negative capacity nodes.
  - Subprocess architecture for `analytics-spark` with Java 17 `--add-opens` flags is clean and keeps Spring Boot POM unpolluted. Approved!
- Handover / Alert for Prasham:
  - Commit `a1c156b` merged `NodeRegistrationServiceTest.java` into `main` without the corresponding implementation classes (`NodeEntity`, `NodeEntityRepository`, `SilentNodeMonitor`, `NodeRegistrationService`). Please commit the missing classes to restore green `mvn test-compile` on `main`.

### Day 3
-

### Day 4
-

### Day 5
-
