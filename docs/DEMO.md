# DRRCP Full Demo Presentation Script & Rehearsal Guide

**Project:** Disaster Relief & Rescue Coordination Platform (DRRCP) — Web Edition  
**Team 4 Members:** Prasham (Core/Coordinator), Grasit (Consensus/FT), Parth (Compute/Spark), Vedant (Comm/HPC/Shell)  
**Target Duration:** 7 Minutes (Strict Academic Evaluation Timing)  
**Primary URL:** `http://localhost:8080/index.html` (or `?mock=1` for offline fallback)  

---

## Presentation Run-Sheet (7-Minute Breakdown)

```
[0:00] ─── Prasham: Intro & Exp 1-3 (Registration, Heartbeats, Clock Sync) ─── [1:45]
[1:45] ─── Grasit: Exp 4, 8, 5 (Leader Election, Failover, CRDT Reconcile) ──── [3:30]
[3:30] ─── Parth: Exp 6-7 (Capacity Load Balancing, Spark Analytics) ───────── [5:00]
[5:00] ─── Vedant: Exp 9-10 & Live Dashboard (MPI Comms, Matrix HPC, Shell) ── [6:30]
[6:30] ─── All: Scenario Runner Summary & Q&A Defense ───────────────────────── [7:00]
```

---

### Minute 0:00 – 1:45: Discovery, Failure Detection & Clock Synchronization
**Presenter:** Prasham  
**Pages:** [`/index.html`](/index.html) ➔ [`/pages/nodes.html`](/pages/nodes.html) ➔ [`/pages/clock.html`](/pages/clock.html)  

- **0:00 - 0:30 (Context & Architecture):**
  - *"Good morning professors. We are Team 4. In a post-disaster earthquake and flood zone, terrestrial communications and power grids are severed. We ported our 10 Distributed Computing console experiments into ONE full-stack reactive web platform."*
  - Point to the **Top Header**, showing `Live Events Stream` and the **Network Status Banner** (`ALL CONNECTED`).
- **0:30 - 1:10 (Exp 1 & 2: Node Registration & Heartbeats):**
  - Navigate to **Node Coordinator** (`/pages/nodes.html`).
  - Point out autonomous nodes (`UAV-01`, `UAV-02`, `Edge-01`) registered via Java RMI on port `1099`.
  - Explain the scheduled 4-worker thread pool monitoring heartbeats every 2 seconds.
  - Demonstrate a silent node: highlight `UAV-02` flagged with red badge `SILENT` when missing heartbeats $>5$ seconds.
- **1:10 - 1:45 (Exp 3: Cristian's Clock Sync & Network Partition):**
  - Navigate to **Clock Sync** (`/pages/clock.html`).
  - Demonstrate Cristian's algorithm: show $t_0, t_1, t_2$, RTT estimation ($12\text{ ms}$), and clock skew correction.
  - Click **"⚡ Simulate Network Partition"**: the Network Banner turns glowing red: `NETWORK PARTITION ACTIVE (ISOLATED CLUSTERS)`. Explain that Cluster-A and Cluster-B are now severed. Hand off to Grasit.

---

### Minute 1:45 – 3:30: Consensus, Primary-Backup Failover & State Reconciliation
**Presenter:** Grasit  
**Pages:** [`/pages/election.html`](/pages/election.html) ➔ [`/pages/faulttolerance.html`](/pages/faulttolerance.html) ➔ [`/pages/reconcile.html`](/pages/reconcile.html)  

- **1:45 - 2:20 (Exp 4: Bully & Ring Leader Election):**
  - Navigate to **Leader Election** (`/pages/election.html`).
  - Explain that because the Cluster Coordinator is unreachable, isolated clusters must elect local emergency coordinators.
  - Click **"Run Bully Election"** for `Cluster-A`: observe message complexity traces and `UAV-02` elected as temporary leader.
  - Click **"Compare Bully vs Ring"**: show the message count chart (Bully $O(N^2)$ vs Ring $2N$).
- **2:20 - 2:55 (Exp 8: Primary-Backup Fault Tolerance):**
  - Navigate to **Fault Tolerance** (`/pages/faulttolerance.html`).
  - Show the Primary Coordinator replicating system updates to the Hot-Standby Backup.
  - Click **"Simulate Primary Crash"**: Primary coordinator turns red `FAILED`. The Backup detects heartbeat loss and promotes itself instantly to active `PRIMARY`.
- **2:55 - 3:30 (Exp 5: CRDT Set Union & Vector Clock Reconciliation):**
  - Navigate to **Reconciliation** (`/pages/reconcile.html`).
  - In the top banner, click **"🔄 Heal Network Partition"**.
  - Click **"Reconcile Updates"**: Show vector clocks $[1, 2, 0]$ detecting concurrent updates across partitions.
  - Explain the CRDT Set Union and Safety Precedence rule (`NEEDS_RESCUE` overrides `RESCUED`). Point to the green `CONFLICT RESOLVED` badge. Hand off to Parth.

---

### Minute 3:30 – 5:00: Load Balancing & Apache Spark Big Data Analytics
**Presenter:** Parth  
**Pages:** [`/pages/loadbalancer.html`](/pages/loadbalancer.html) ➔ [`/pages/analytics.html`](/pages/analytics.html)  

- **3:30 - 4:15 (Exp 6: Capacity-Weighted Load Balancing):**
  - Navigate to **Load Balancer** (`/pages/loadbalancer.html`).
  - Explain the **Effective Capacity** formula:
    $$\text{effectiveCapacity} = \text{maxCapacity} \times \frac{\text{battery} + \text{signal}}{2}$$
  - Toggle `UAV-01` to `UNAVAILABLE`.
  - Click **"⚡ Assign All Pending"**: 12 rescue tasks route dynamically to candidate nodes with the lowest projected utilization. Show that the disabled node received zero tasks.
- **4:15 - 5:00 (Exp 7: Spark Analytics Subprocess):**
  - Navigate to **Analytics & Spark** (`/pages/analytics.html`).
  - Explain the dual architecture: Apache Spark launched as a subprocess via `ProcessBuilder` with automatic Java-streams fallback.
  - Click **"⚡ Run Analytics Pipeline"**: Show the MapReduce execution aggregation casualties by sector and resource burn rates visualized on Chart.js. Hand off to Vedant.

---

### Minute 5:00 – 6:30: Collective Communication, Parallel Matrix HPC & Live Command Center
**Presenter:** Vedant  
**Pages:** [`/pages/comm.html`](/pages/comm.html) ➔ [`/pages/matrix.html`](/pages/matrix.html) ➔ [`/index.html`](/index.html)  

- **5:00 - 5:45 (Exp 9: MPI Collective Communication):**
  - Navigate to **Collective Comm** (`/pages/comm.html`).
  - Explain that in disaster relief, individual socket sends cause bottlenecks. Collective communication uses tree topologies ($O(\log P)$).
  - Click **"🚀 Run Full Simulation"**:
    - **`MPI_Bcast`**: Purple vector arrows broadcast flash flood warning from Root 0 to all ranks.
    - **`MPI_Scatter`**: Blue vector arrows distribute disjoint supply packages to each rank.
    - **`MPI_Gather`**: Green vector arrows collect casualty reports back into Root.
  - Point to the **Self-Check Badge**: `🛡️ SELF-CHECK: PASSED (100% Verified)`.
- **5:45 - 6:15 (Exp 10: Parallel Matrix Multiplication HPC):**
  - Navigate to **Parallel Matrix** (`/pages/matrix.html`).
  - Explain the domain context: multiplying Sector Risk Factors (Matrix A) $\times$ Emergency Resource Demands (Matrix B) to compute Priority Scores.
  - Select `400 x 400` dimension, **4 Threads**, and click **"⚡ Run Benchmark"**:
    - Sequential single-threaded time: $\approx 145\text{ ms}$.
    - Parallel multi-threaded time: $\approx 42\text{ ms}$.
    - Speedup: $\approx 3.45\times$, Efficiency: $\approx 86\%$.
  - Click **"📈 Sweep Threads (1..8)"**: show empirical speedup versus Amdahl's Law theoretical line on Chart.js.
- **6:15 - 6:30 (Live Mission Command Dashboard & Toast System):**
  - Navigate back to **Dashboard Home** (`/index.html`).
  - Show all 6 live KPI cards, the dynamic Events by Module doughnut chart, and trigger a quick action to show real-time SSE updates and global toast notifications.

---

### Minute 6:30 – 7:00: Full Story Scenario Runner & Q&A Defense
**Presenter:** All Team Members  
**Pages:** [`/pages/scenario.html`](/pages/scenario.html)  

- Open **Scenario Runner** (`/pages/scenario.html`).
- Show the 7-Phase Stepper that executes the entire 10-experiment story in automated sequence (Boot ➔ Clock Sync ➔ Partition ➔ Election ➔ Partitioned Tasks ➔ Heal ➔ Reconcile).
- Conclude: *"Thank you. We are ready for your questions."*

---

## Demo Troubleshooting & Fallback Cheat-Sheet

| Symptom | Cause | Instant Fix |
|---|---|---|
| **Port 1099 busy** | Previous RMI registry process holding port | Kill previous java process or restart app (`mvn spring-boot:run`) |
| **Spark subprocess slow** | Host system memory limit on Spark JVM | SparkJobService automatically falls back to in-memory Java Streams; results are identical |
| **No backend running** | Testing on presentation laptop without Java 17 | Click the `🔌 LIVE API` badge in top header to switch to `⚡ MOCK ACTIVE` (`?mock=1`). All 10 pages work 100% offline |
| **Browser console warnings** | Browser strict autoplay or SSE reconnect | Normal SSE behavior; auto-reconnects with exponential backoff |
