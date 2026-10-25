# Disaster Relief & Rescue Coordination Platform — Unified System (Exp 1–5)

This folder merges all 5 Distributed Computing experiments into **one running
system** instead of 5 separate demos. Think of it like 5 separate car parts
(engine, gearbox, brakes, steering, wheels) that were built and tested one at
a time in the earlier experiments — this is the same parts bolted onto one
car and driven around the block.

Nothing here is copy-pasted unchanged: the code was restructured so that
each experiment's logic now **triggers** the next one in a real chain,
instead of each one just being its own standalone `main()`.

## How the 5 experiments are wired together

| Experiment | What it becomes in the unified system |
|---|---|
| **Exp 1** (RPC/RMI registration + connectivity score) | `server.ClusterCoordinatorImpl.registerNode()` — every node registers with the real coordinator over Java RMI when the system starts. |
| **Exp 2** (multithreading + silence detection) | The coordinator processes registration/heartbeats on a 4-thread pool, and a background `MonitorThread` continuously watches for nodes that go silent. |
| **Exp 3** (Cristian's Algorithm + Vector Clocks) | Each node corrects its clock against the coordinator **before** anything else happens (Cristian's). Every node also carries a `VectorClock` from the moment it starts, which keeps ticking through the whole run. |
| **Exp 4** (Bully & Ring election) | The moment the simulated network partition happens and the real coordinator becomes unreachable, **Cluster-A automatically runs both the Bully and the Ring algorithm** to pick a temporary local leader — this isn't a separate demo any more, it's triggered by the actual "coordinator unreachable" event. |
| **Exp 5** (reconciliation / CRDT merge) | Every update any node makes — while connected or while partitioned — is tagged with its vector clock. Once the partition heals, `ReconciliationEngine` (fed by the *same* vector clocks from Exp 3) merges everything: safety-first precedence for conflicting rescue statuses, CRDT union for the survivor list. |

## The story the demo tells

1. Three nodes (`UAV-01`, `UAV-02` in **Cluster-A**, `Edge-01` in **Cluster-B**)
   boot up, register with the central Cluster Coordinator over RMI, correct
   their clocks with Cristian's Algorithm, and start sending heartbeats.
2. A network partition is simulated. The coordinator becomes unreachable.
   Cluster-A, left with no central authority, elects a temporary local
   leader using **both** Bully and Ring (so you can compare message counts
   on the same failure).
3. While cut off, both clusters keep working independently: UAV-01 marks
   `House-12` as `RESCUED`; at the same time, on the other side of the
   partition, `Edge-01` marks `House-12` as `NEEDS_RESCUE`. Each survivor
   name found is added to a shared `confirmedSurvivors` list. Every one of
   these updates is stamped with a vector clock.
4. The partition heals. All nodes resume heartbeats to the real coordinator.
5. The reconciliation engine looks at every update's vector clock: the two
   `House-12` updates are genuinely concurrent (neither vector "happened
   before" the other), so the safety-first rule applies and `NEEDS_RESCUE`
   wins (it's flagged for human re-verification). The two different
   survivor names are **not** a conflict at all — they're unioned
   automatically, CRDT-style.

## Folder layout

```
DisasterReliefSystem/
  common/     shared classes: NodeService (RMI interface), NodeStatus,
              VectorClock, UpdateRecord, ElectionNode, ReconciliationEngine
  server/     ClusterCoordinator (main) + ClusterCoordinatorImpl
              (Exp 1 registration/scoring + Exp 2 thread pool/monitor + Exp 3 time server)
  election/   BullyElection.java, RingElection.java (Exp 4, unchanged logic,
              now invoked automatically when a partition is detected)
  client/     ClusterNode (one thread per physical node, runs phase 1 → 2 → 3),
              LocalBus (simulated wireless link), DisasterReliefClient (main
              orchestrator: builds the 3 nodes, the partition, the reconnect,
              and finally calls the reconciliation engine)
```

## How to build

You need JDK 17+ (any recent JDK works — this was built and tested on
OpenJDK 21). No external libraries, no build tool needed — just `javac`.

```bash
cd DisasterReliefSystem
javac -d out common/*.java server/*.java election/*.java client/*.java
```

This compiles everything into an `out/` folder.

## How to run it (two terminals)

**Terminal 1 — start the Cluster Coordinator first and leave it running:**

```bash
cd DisasterReliefSystem/out
java server.ClusterCoordinator
```

You should see:
```
Cluster Coordinator ready on port 1099 as 'Coordinator'
Worker pool: 4 threads | Monitor: active (threshold=6000ms)
```

**Terminal 2 — run the full node simulation:**

```bash
cd DisasterReliefSystem/out
java client.DisasterReliefClient
```

Watch **both** terminals — Terminal 1 shows the coordinator's point of view
(registrations, heartbeats landing on different pool threads), Terminal 2
shows the nodes' point of view (clock sync, the partition, the election,
the local updates, and finally the reconciliation report).

When Terminal 2 finishes and prints `Full pipeline complete`, you can stop
the coordinator in Terminal 1 with `Ctrl+C`.

## Run it again with a different scenario

Everything that makes the demo "the demo" lives in
`client/DisasterReliefClient.java` — nothing else needs to change:

- **Change who's in which cluster / their battery+signal (Exp 1 scores):**
  edit the three `new ClusterNode(...)` calls at the bottom.
- **Change what happens during the partition (Exp 5 conflicts):** edit the
  `uav1Partition` / `uav2Partition` / `edge1Partition` action lists — each
  `ClusterNode.Action.update(...)` is one local write, and
  `ClusterNode.Action.updateAndSend(...)` also forwards it to a specific
  node inside the same cluster.
- **Change who initiates the election, or add more nodes to Cluster-A
  (Exp 4):** edit the `forBully` / `forRing` lists inside the
  `afterPhase1` barrier action.
- **Make the clock drift worse/better (Exp 3):** the 5th constructor
  argument to `ClusterNode` is `injectedSkewMillis` — the artificial clock
  error Cristian's Algorithm has to correct.
- **Make the coordinator declare a node "silent" faster/slower (Exp 2):**
  change `threshold` in `server/ClusterCoordinator.java`
  (`coordinator.checkForSilentNodes(6000)`).

## Notes for a beginner

- Java RMI is just a way for one JVM to call a method that actually runs in
  a *different* JVM (here: the client JVM calling code that runs inside the
  coordinator JVM), as if it were a normal local method call. That's why
  you need Terminal 1 running *before* Terminal 2 — the client needs
  something to call.
- The "partition" isn't a real network outage — it's simulated in code by
  a boolean flag (`LocalBus.partitioned`) that starts blocking messages
  between clusters. This is the same trick real distributed-systems papers
  use to test this kind of logic without needing real, unreliable hardware.
- A `CyclicBarrier` is used to make sure all 3 node threads finish "Phase 1"
  together before the partition is declared, and all finish "Phase 2"
  together before reconnection is declared — like a coach blowing a
  whistle only once everyone's back on the line.

---

## Experiment 9 — Collective Communication: Broadcast, Scatter, Gather

Folder: `Experiment9/` (plain Java, default package, same style as `Experiment8`).

Java has no standard MPI binding, so MPI is **simulated with threads**: each
thread is one "rank", and a small `Communicator` class plays the role of
`MPI_COMM_WORLD`. The three collectives mirror the real MPI calls:

| MPI call | Method | What it shows |
|---|---|---|
| `MPI_Bcast`   | `Communicator.bcast`   | Root (rank 0, the Command Centre) sends the **same** alert to every rank |
| `MPI_Scatter` | `Communicator.scatter` | Root splits an array into equal chunks; each rank gets a **different** chunk |
| `MPI_Gather`  | `Communicator.gather`  | Every rank sends its chunk back; root assembles them **in rank order** |

Files: `Communicator.java` (the simulated communicator + the three collectives),
`CollectiveCommunicationDemo.java` (main program, run by every rank).

```bash
cd DisasterReliefSystem/Experiment9
javac *.java
java CollectiveCommunicationDemo              # 4 processes, chunk size 2
java CollectiveCommunicationDemo 6 3          # 6 processes, chunk size 3
```

The array size is `processes x chunkSize`, so it always divides evenly for any
number of processes. The program ends with a self-check that the gathered buffer
matches what was scattered (rank order preserved).
