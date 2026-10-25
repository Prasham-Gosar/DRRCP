# Architecture

## 1. Folder layout

```
disaster-relief-web/
├── pom.xml
├── README.md
├── .github/pull_request_template.md
├── docs/ARCHITECTURE.md, docs/PROGRESS.md
├── legacy/                         original console experiments (read-only reference)
├── analytics-spark/                Exp 7 Spark module (Parth) — separate Maven module, launched as subprocess
├── data/                           H2 database files (git-ignored)
└── src/
    ├── main/java/com/drs/
    │   ├── DrsApplication.java
    │   ├── core/                   Prasham — Exp 1,2,3, partition, scenario runner, event bus
    │   │   ├── config/             SchedulingConfig
    │   │   ├── controller/         CoordinatorController
    │   │   ├── event/              EventPublisher, EventLog, EventLogRepository, EventController
    │   │   ├── model/              NodeStatus (JPA entity, Serializable), NodeStatusRepository
    │   │   ├── rmi/                NodeService (RMI contract; registry on 1099 comes later)
    │   │   └── service/            CoordinatorService
    │   ├── consensus/              Grasit — Exp 4 (Bully/Ring), Exp 5 (vector clock + CRDT)
    │   ├── faulttolerance/         Grasit — Exp 8 (primary-backup)
    │   ├── compute/                Parth  — Exp 6 (load balancing), Exp 7 (MapReduce/Spark client)
    │   └── comm/                   Vedant — Exp 9 (broadcast/scatter/gather), Exp 10 (parallel matrix)
    ├── main/resources/
    │   ├── application.properties
    │   └── static/                 index.html, css/app.css, js/api.js, js/events.js, js/layout.js (Vedant),
    │                               each person's own pages
    └── test/                       JUnit 5 + MockMvc; uses in-memory H2
```

## 2. Packages and owners

| Package | Owner | Experiments |
|---|---|---|
| `com.drs.core` | Prasham | 1 RMI registration, 2 heartbeats/thread pool, 3 Cristian + vector clocks, partition simulation, scenario runner, EventPublisher |
| `com.drs.consensus` | Grasit | 4 Bully/Ring election, 5 reconciliation + CRDT |
| `com.drs.faulttolerance` | Grasit | 8 primary-backup replication |
| `com.drs.compute` | Parth | 6 load balancing, 7 MapReduce/Spark |
| `com.drs.comm` | Vedant | 9 MPI-style collectives, 10 parallel matrix; frontend shell |

Never edit another person's package without telling them.

## 3. Conventions

* REST under `/api/...`, JSON only. Thin `@RestController`, logic in `@Service`.
* No `System.out` in services — return a result object with a `steps` list for the UI and call
  `EventPublisher.publish(module, type, payload)` for live events.
* Branch `feature/<name>-<topic>`, PR to `main`, 1 review.

## 4. Event bus

`EventPublisher.publish(String module, String type, Object payload)`:
1. serialises payload to JSON, 2. saves an `EventLog` row, 3. pushes to every SSE subscriber.

`GET /api/events` (SSE) sends **unnamed** events, so `new EventSource('/api/events').onmessage` receives all of them.
Each message `data` is:

```json
{"id": 12, "module": "coordinator", "type": "NODE_REGISTERED", "payload": "{...json string...}", "timestamp": "2026-10-25T07:00:00Z"}
```

`payload` is a JSON **string** — call `JSON.parse(msg.payload)` in the browser.

## 5. REST contract (agreed in the project plan — everyone follows this)

Status: **built** = exists on main, **temp** = Day 1 placeholder that Day 2 replaces, **planned** = owner implements it on the day shown in the plan.

### core (Prasham)

| Endpoint | Purpose | Status |
|---|---|---|
| `POST /api/nodes/register` | Exp1 register node, returns connectivity score | planned (Day 2) |
| `GET /api/nodes`, `GET /api/nodes/{id}` | List/get nodes with status ALIVE/SILENT | planned (Day 2) |
| `POST /api/nodes/{id}/heartbeat` | Exp2 heartbeat, handled by 4-thread pool | planned (Day 2) |
| `POST /api/simulator/start`, `/stop`, `/silence/{nodeId}` | Spawn simulated UAV/edge nodes | planned (Day 2) |
| `POST /api/clock/sync/{nodeId}`, `GET /api/clock/status` | Exp3 Cristian sync, vector clocks | planned (Day 3) |
| `POST /api/network/partition`, `/heal`, `GET /api/network/status` | Simulated partition | planned (Day 3) |
| `POST /api/scenario/run`, `GET /api/scenario/status`, `POST /api/scenario/reset` | Full story runner | planned (Day 4) |
| `GET /api/events` (SSE), `GET /api/events/history?limit=50` | Live events | **built** |

Temporary Day 1 endpoints (replaced by the `/api/nodes/...` ones on Day 2, do not build on these):

| Endpoint | Purpose | Status |
|---|---|---|
| `POST /api/coordinator/register` | Register node | temp |
| `GET /api/coordinator/nodes`, `/nodes/{id}`, `/nodes/{id}/state` | Read nodes | temp |
| `POST /api/coordinator/heartbeat/{nodeId}` | Heartbeat | temp |
| `GET /api/coordinator/time` | Cristian time server | temp |

### consensus + faulttolerance (Grasit)

| Endpoint | Purpose |
|---|---|
| `POST /api/election/bully`, `/ring`, `/compare`, `GET /api/election/history` | Exp4 |
| `POST /api/updates`, `GET /api/updates`, `DELETE /api/updates` | Exp5 stamped updates |
| `POST /api/reconcile`, `GET /api/reconcile/latest` | Exp5 merge |
| `POST /api/ft/updates`, `POST /api/ft/primary/fail`, `POST /api/ft/reset`, `GET /api/ft/state` | Exp8 |

### compute (Parth)

| Endpoint | Purpose |
|---|---|
| `POST /api/tasks`, `GET /api/tasks`, `POST /api/tasks/{id}/assign`, `DELETE /api/tasks` | Rescue tasks |
| `POST /api/loadbalancer/assign-all`, `GET /api/loadbalancer/nodes` | Exp6 |
| `POST /api/analytics/mapreduce/run`, `GET /api/analytics/mapreduce/latest`, `GET /api/analytics/mapreduce/history` | Exp7 |

### comm (Vedant)

| Endpoint | Purpose |
|---|---|
| `POST /api/comm/bcast`, `/scatter`, `/gather`, `/run-all` | Exp9 |
| `POST /api/matrix/run`, `POST /api/matrix/sweep` | Exp10 |

## 6. Event types

Format on `/api/events`: unnamed SSE messages, so `new EventSource('/api/events').onmessage` gets all of them.
Each message `data` is:

```json
{"id": 12, "module": "core", "type": "NODE_REGISTERED", "payload": "{...json string...}", "timestamp": "2026-10-25T07:00:00Z"}
```

`payload` is a JSON **string**: call `JSON.parse(msg.payload)` in the browser.

| Module | Types |
|---|---|
| `core` | NODE_REGISTERED, HEARTBEAT, NODE_SILENT, CLOCK_SYNCED, NETWORK_PARTITIONED, NETWORK_HEALED, SCENARIO_PHASE |
| `consensus` | ELECTION_STARTED, LEADER_ELECTED, RECONCILED |
| `ft` | FT_REPLICATED, FT_PRIMARY_FAILED, FT_BACKUP_PROMOTED |
| `compute` | TASK_CREATED, TASK_ASSIGNED, ANALYTICS_STARTED, ANALYTICS_DONE |
| `comm` | COMM_BCAST, COMM_SCATTER, COMM_GATHER, MATRIX_DONE |

Day 1 temporary names (replaced on Day 2, do not listen for these): module `coordinator` / `heartbeat` with types `NODE_REGISTERED`, `HEARTBEAT_RECEIVED`, `PARTITION_SUSPECTED`.

Owners may add new types in their own module; add a row here in the same PR.

## 7. Connectivity score (Exp 1, unchanged from legacy)

`score = 2 × signal + battery` → `>=150 STABLE`, `>=100 AT_RISK`, `>=50 PARTITION_LIKELY`, else `PARTITIONED`.
Silence threshold 6000 ms, monitor every 1 s, worker pool 4 threads.