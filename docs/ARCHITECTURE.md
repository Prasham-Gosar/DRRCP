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

## 5. REST contract

Status: **built** = exists now, **planned** = agreed contract, owner implements it.

### core (Prasham)

| Method | Path | Body / params | Returns | Status |
|---|---|---|---|---|
| POST | `/api/coordinator/register` | `{nodeId, batteryLevel 0-100, signalStrength 0-100, nodeType, clusterId}` | `{nodeId, state, connectivityScore, thread, steps[]}` | built |
| GET | `/api/coordinator/nodes` | — | `NodeStatus[]` | built |
| GET | `/api/coordinator/nodes/{nodeId}` | — | `NodeStatus` or 404 | built |
| GET | `/api/coordinator/nodes/{nodeId}/state` | — | `{nodeId, state}` or 404 | built |
| POST | `/api/coordinator/heartbeat/{nodeId}` | optional `?timestamp=<epochMs>` | `{nodeId, timestamp, thread, steps[]}` | built |
| GET | `/api/coordinator/time` | — | `{serverTime, note}` (Cristian server) | built |
| GET | `/api/events` | — | SSE stream | built |
| GET | `/api/events/history` | `?limit=50` (1-500) | `EventLog[]`, newest first | built |
| POST | `/api/clock/sync` | `{nodeId}` | `{offsetMs, rttMs, steps[]}` | planned |
| POST | `/api/clock/vector/tick` | `{nodeId}` | `{nodeId, vector{}}` | planned |
| POST | `/api/partition/start` | `{clusters[]}` | `{partitionId, steps[]}` | planned |
| POST | `/api/partition/heal` | — | `{steps[]}` | planned |
| POST | `/api/scenario/run` | `{name}` | `{steps[]}` | planned |

### consensus (Grasit) — all planned

| Method | Path | Returns |
|---|---|---|
| POST | `/api/election/bully` | `{leader, messages, steps[]}` |
| POST | `/api/election/ring` | `{leader, messages, steps[]}` |
| POST | `/api/reconcile/update` | `{updateId, vector{}}` |
| POST | `/api/reconcile/merge` | `{resolutions[], survivors[], steps[]}` |
| POST | `/api/replication/write` | `{seq, replicatedTo[], steps[]}` |
| POST | `/api/replication/kill-primary` | `{newPrimary, steps[]}` |
| GET | `/api/replication/state` | `{primary, backups[], log[]}` |

### compute (Parth) — all planned

| Method | Path | Returns |
|---|---|---|
| POST | `/api/loadbalancer/assign` | `{assignments[], steps[]}` |
| GET | `/api/loadbalancer/nodes` | `LoadNode[]` |
| POST | `/api/analytics/run` | `{jobId}` |
| GET | `/api/analytics/{jobId}` | `{status, results[]}` |

### comm (Vedant) — all planned

| Method | Path | Returns |
|---|---|---|
| POST | `/api/mpi/broadcast` | `{receivers[], steps[]}` |
| POST | `/api/mpi/scatter` | `{chunks[], steps[]}` |
| POST | `/api/mpi/gather` | `{gathered[], steps[]}` |
| POST | `/api/matrix/multiply` | `{size, sequentialMs, parallelMs, speedup}` |

## 6. Event types

| Module | Type | Emitted by | Payload |
|---|---|---|---|
| `coordinator` | `NODE_REGISTERED` | CoordinatorService | RegistrationResult |
| `heartbeat` | `HEARTBEAT_RECEIVED` | CoordinatorService | HeartbeatResult |
| `heartbeat` | `PARTITION_SUSPECTED` | silent-node monitor (once per silence) | `{nodeId, silentForMs, thresholdMs}` |
| `clock` | `CLOCK_SYNCED`, `VECTOR_TICK` | core (planned) | — |
| `partition` | `PARTITION_STARTED`, `PARTITION_HEALED` | core (planned) | — |
| `election` | `ELECTION_STARTED`, `LEADER_ELECTED` | consensus (planned) | — |
| `reconcile` | `UPDATE_RECORDED`, `CONFLICT_RESOLVED`, `MERGE_COMPLETE` | consensus (planned) | — |
| `replication` | `WRITE_REPLICATED`, `PRIMARY_FAILED`, `BACKUP_PROMOTED` | faulttolerance (planned) | — |
| `loadbalancer` | `TASK_ASSIGNED` | compute (planned) | — |
| `analytics` | `JOB_STARTED`, `JOB_COMPLETE` | compute (planned) | — |
| `mpi` | `BROADCAST`, `SCATTER`, `GATHER` | comm (planned) | — |
| `matrix` | `MATRIX_DONE` | comm (planned) | — |

Owners add rows for their own events in their PRs.

## 7. Connectivity score (Exp 1, unchanged from legacy)

`score = 2 × signal + battery` → `>=150 STABLE`, `>=100 AT_RISK`, `>=50 PARTITION_LIKELY`, else `PARTITIONED`.
Silence threshold 6000 ms, monitor every 1 s, worker pool 4 threads.
