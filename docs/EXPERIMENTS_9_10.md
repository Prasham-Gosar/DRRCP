# Experiments 9 & 10 — Collective Communication & Parallel Matrix HPC

**Module Owner:** Vedant (`com.drs.comm` & Frontend Shell)  
**Project:** Disaster Relief & Rescue Coordination Platform (DRRCP) — Web Edition  
**Stack:** Java 17, Spring Boot 3.3.x, ThreadPool / CyclicBarrier, Server-Sent Events (SSE), Chart.js, Vanilla CSS Design System  
**Branch:** `feature/vedant-ui-qa` → merged to `main` as v1.0  

---

## Table of Contents

1. [Experiment 9: MPI Collective Communication & Synchronization](#experiment-9)
   - [Aim & System Model](#exp-9-aim)
   - [Algorithm & Mathematical Formulations](#exp-9-algorithm)
   - [Code Map & Package Architecture](#exp-9-code-map)
   - [UI Demonstration Walkthrough](#exp-9-ui-demo)
   - [Viva Q&A (5 Questions)](#exp-9-viva)
2. [Experiment 10: Parallel Matrix Multiplication & HPC Benchmarks](#experiment-10)
   - [Aim & System Model](#exp-10-aim)
   - [Algorithm & Amdahl's Law](#exp-10-algorithm)
   - [Code Map & Package Architecture](#exp-10-code-map)
   - [UI Demonstration Walkthrough](#exp-10-ui-demo)
   - [Viva Q&A (5 Questions)](#exp-10-viva)
3. [Building & Verification Instructions](#building)
4. [REST API Quick Reference](#api-reference)

---

<a name="experiment-9"></a>
## Experiment 9: MPI Collective Communication & Synchronization

<a name="exp-9-aim"></a>
### Aim & System Model

In disaster relief operations, command stations and autonomous field units (UAV drones, ground medical squads) require **synchronized group communication** rather than disjoint point-to-point messages. Experiment 9 models and visualises the three foundational **Message Passing Interface (MPI)** collective communication primitives across a distributed cluster communicator (`MPI_COMM_WORLD`):

| Primitive | MPI Equivalent | Disaster Relief Role |
|---|---|---|
| One-to-All Broadcast | `MPI_Bcast` | Root dispatches the same critical emergency alert identically to every rescue unit |
| One-to-All Partition | `MPI_Scatter` | Root splits a geographic zone-search payload into disjoint chunks — one chunk per team |
| All-to-One Assembly | `MPI_Gather` | All teams report local triage data; root assembles in strict ascending rank order |
| Collective Sync | `MPI_Barrier` | No process advances until every rank has received its partitioned input |

```
                  ┌──────────────────────────────────────────────┐
                  │          Command Centre (Root Rank 0)        │
                  └──────────────────────┬───────────────────────┘
                                         │
        ┌───────────────────────────────┬┴───────────────────────────────┐
        │ MPI_Bcast (Identical Alert)   │ MPI_Scatter (Disjoint Chunks)  │ MPI_Gather
        ▼                               ▼                                ▲
┌───────────────┐               ┌───────────────┐               ┌───────────────┐
│ Rank 1: Squad │               │ Rank 2: UAV   │               │ Rank 3: Base  │
└───────────────┘               └───────────────┘               └───────────────┘
```

---

<a name="exp-9-algorithm"></a>
### Algorithm & Mathematical Formulations

#### 1. `MPI_Bcast` — One-to-All Identical Distribution

Root rank $r$ holds message buffer $M$. After broadcast:

$$\forall i \in \{0, 1, \dots, P-1\}, \quad \text{Buffer}_i = M$$

- **Flat Model (naïve):** Root sends $P-1$ sequential messages:
  $$T_{\text{flat}} = (P - 1)(\alpha + n\beta)$$
- **Binomial Tree (optimised MPI):** Each branch recurses concurrently:
  $$T_{\text{tree}} = \lceil \log_2 P \rceil \cdot (\alpha + n\beta)$$
  *Where $\alpha$ = network start-up latency, $n$ = payload bytes, $\beta$ = inverse bandwidth.*

#### 2. `MPI_Scatter` — One-to-All Disjoint Partitioning

Root holds array $A$ of size $P \times K$ ($K$ = chunk size). Process $i$ receives:

$$B_i[j] = A[i \cdot K + j], \quad \forall j \in \{0, \dots, K-1\}$$

- Total transfer volume: $(P - 1) \cdot K$ elements.
- Constraint: $N = P \times K$ elements (perfectly balanced).

#### 3. `MPI_Gather` — All-to-One Assembly

Each process $i$ holds local array $C_i$ of size $K$. Root assembles:

$$D[i \cdot K + j] = C_i[j], \quad \forall i \in \{0, \dots, P-1\},\; j \in \{0, \dots, K-1\}$$

#### 4. Automated Self-Check Verification

To verify end-to-end communication correctness without human inspection:

- Root generates: $A[k] = 2k$
- Each rank $i$ computes two local buffers:
  - $B_i[j] = A_i[j] + 10$ → gathered back as `gatherB`
  - $C_i[j] = A_i[j] - 10$ → gathered back as `gatherC`
- Root evaluates:
  $$S[k] = \frac{B[k] + C[k]}{2} = \frac{(A[k]+10)+(A[k]-10)}{2} = A[k]$$
- If $\forall k \in [0, P \times K - 1],\; S[k] = A[k]$ → `selfCheckPassed = true`.

---

<a name="exp-9-code-map"></a>
### Code Map & Package Architecture

| Component | Path | Responsibility |
|---|---|---|
| **MPI Simulator** | `com.drs.comm.communicator.Communicator` | Pure collective engine: `bcastText`, `bcast`, `scatter`, `gather`, `barrier`, self-check; ported from legacy Experiment 9 |
| **Request DTO** | `com.drs.comm.model.CommRequest` | Fields: `numProcesses` (2–16), `chunkSize` (1–10), `rootRank`, `alertText` |
| **Result DTO** | `com.drs.comm.model.CommResult` | Fields: `operation`, `processes`, `scattered[]`, `gathered[]`, `timeline[]`, `steps[]`, `selfCheckPassed`, `durationMs` |
| **Service** | `com.drs.comm.service.CommService` | Validates bounds → executes operations → publishes `COMM_BCAST`, `COMM_SCATTER`, `COMM_GATHER` via `EventPublisher` |
| **Controller** | `com.drs.comm.controller.CommController` | `POST /api/comm/bcast`, `/scatter`, `/gather`, `/run-all` · `GET /api/comm/status` |
| **UI Page** | `static/pages/comm.html` | Rank-box grid, animated SVG bezier arrows, process/chunk/root controls, self-check badge |
| **UI Script** | `static/js/comm.js` | Rank rendering, SVG flow animation, mock fallback (`?mock=1`), SSE live log |
| **Tests** | `CommunicatorTest`, `CollectiveCommServiceTest`, `CollectiveCommControllerTest` | 16 JUnit 5 + MockMvc tests — 0 failures |

---

<a name="exp-9-ui-demo"></a>
### UI Demonstration Walkthrough

> **URL:** `http://localhost:8080/pages/comm.html` (or sidebar → **Collective Comm**)  
> **Quick offline test:** append `?mock=1` for instant local simulation without a running server.

**Step 1 — Configure the Communicator (30 sec)**  
In the *Communicator Parameters* card:
- Set **Processes** = `4 Ranks (Standard Squad)`
- Set **Chunk Size** = `2 Zones / Rank`
- Set **Root Rank** = `0` (Command Centre)
- Optionally click **🎲 Preset** to cycle through 4 realistic emergency alert messages.

**Step 2 — Inspect the Rank Topology Canvas**  
The central canvas shows 4 rank boxes arranged horizontally. Rank 0 is highlighted purple as "Command Centre". Bezier arc lines connect it to every worker rank.

**Step 3 — Execute MPI_Bcast**  
Click **"Bcast Alert"** in the Step-by-Step MPI Primitives card.
- Purple SVG arrows animate outward from Rank 0 to Ranks 1, 2, 3.
- Each rank box shows the received alert in the "Alert Received" field.
- Per-Rank Timeline table logs `BCAST_DISPATCHED` (Root) and `BCAST_RECEIVED` (Workers).

**Step 4 — Execute MPI_Scatter**  
Click **"Scatter Zones"**.
- Blue arrows distribute disjoint zone chunks: Rank 0→`[ZONE-A1, ZONE-A2]`, Rank 1→`[ZONE-B1, ZONE-B2]`, etc.
- Scattered Zones buffer in the Verification card shows all distributed zone tags.

**Step 5 — Execute MPI_Gather**  
Click **"Gather Reports"**.
- Green arrows converge from all workers back to Rank 0.
- Gathered Buffer shows mission reports in strict ascending rank order.

**Step 6 — Full Simulation with Self-Check**  
Click **"🚀 Run Full Simulation"**.
- All three phases execute sequentially, each preceded by a `MPI_Barrier`.
- **Self-Check Badge** turns glowing emerald: `🛡️ SELF-CHECK PASSED`.
- Chronological Step Trace terminal shows barrier logs and validation output.

---

<a name="exp-9-viva"></a>
### Viva Examination Q&A — Experiment 9

#### Q1: What is the fundamental difference between `MPI_Bcast` and `MPI_Scatter`?

**Answer:**  
`MPI_Bcast` replicates the **exact same data buffer** to every rank — all $P$ processes receive identical content, so the data volume per rank equals the total send volume.  
`MPI_Scatter` divides the root's source array into **equal, disjoint partitions** of size $K$: Rank $i$ receives exclusively elements $A[i \cdot K]$ through $A[(i+1) \cdot K - 1]$. No two ranks receive the same slice. Our platform enforces $N = P \times K$ to guarantee perfect balance; mismatches return HTTP 400.

---

#### Q2: Why is collective communication preferred over multiple point-to-point `MPI_Send`/`MPI_Recv` calls?

**Answer:**  
A naive loop of $P-1$ individual sends from Root serialises all traffic through one process, giving $O(P)$ latency and creating a network bottleneck. Native MPI collective implementations use **binomial or hypercube tree** topologies where nodes forward messages concurrently, reducing latency to $O(\log_2 P)$. Additionally, MPI runtimes exploit hardware multicast primitives and shared-memory DMA for co-located ranks — benefits impossible with hand-written point-to-point loops.

---

#### Q3: What is `MPI_Barrier` and does it transfer application data?

**Answer:**  
`MPI_Barrier` is a **global synchronisation point** that blocks each calling process until every rank in the communicator has reached the same barrier call. It transfers **zero application data** — its sole function is phase coordination. In our disaster platform, a barrier before the Gather phase guarantees that all UAVs have completed their local zone search before the Command Centre initiates assembly. This prevents partial or disordered data from corrupting the gathered report.

---

#### Q4: How does the automated self-check formula verify Experiment 9 data integrity?

**Answer:**  
The verification uses complementary arithmetic to test the complete scatter–compute–gather round trip:  
1. Root generates $A[k] = 2k$ (source truth).  
2. Root scatters $A$ into equal partitions.  
3. Each rank $i$ computes $B_i[j] = A_i[j] + 10$ and $C_i[j] = A_i[j] - 10$ independently.  
4. Root gathers both $B$ and $C$ buffers.  
5. Root evaluates $S[k] = (B[k] + C[k]) / 2$.  
Any dropped packet, reordered chunk, or thread corruption would break $S[k] = A[k]$. Exact element-by-element equality proves the entire scatter → barrier → compute → gather pipeline is mathematically verified.

---

#### Q5: What happens if the total array size $N$ is not divisible by $P$, and how does DRRCP handle it?

**Answer:**  
In standard OpenMPI, `MPI_Scatter` requires `sendcount` to be identical for all ranks. If $N \bmod P \neq 0$, the programmer must either pad the array or use `MPI_Scatterv` (vector-scatter), which takes per-rank count and displacement arrays. In DRRCP, `CommService` validates at the REST boundary: `processes` $\in [2, 16]$ and `chunkSize` $\in [1, 10]$ guarantee $N = P \times K$ is always a perfect multiple. An invalid combination returns HTTP 400 with a descriptive JSON body, preventing any partial scatter from entering the system.

---

<a name="experiment-10"></a>
## Experiment 10: Parallel Matrix Multiplication & HPC Benchmarks

<a name="exp-10-aim"></a>
### Aim & System Model (Disaster Risk Tensor)

In a catastrophic disaster, emergency logistics centres must compute **real-time sector priority scores** by cross-multiplying spatial hazard assessments with operational resource demand profiles. As the disaster grid scales to hundreds of sectors ($N \times N$), sequential computation creates unacceptable lag.

Experiment 10 implements **Parallel Matrix Multiplication using 1D Row Decomposition** and measures empirical speedup versus a single-threaded sequential baseline across scaling thread counts (1, 2, 4, 8, 16):

$$\underbrace{A_{N \times N}}_{\text{Zone-Risk Tensor}} \times \underbrace{B_{N \times N}}_{\text{Resource Demand}} = \underbrace{C_{N \times N}}_{\text{Priority Score Matrix}}$$

---

<a name="exp-10-algorithm"></a>
### Algorithm & Amdahl's Law Scaling

#### 1. Matrix Multiplication (Sequential Baseline)

$$C_{i,j} = \sum_{k=0}^{N-1} A_{i,k} \cdot B_{k,j}, \quad \forall i, j \in [0, N-1]$$

Total complexity: $O(N^3)$ operations ($N^3$ multiplications + $N^3 - N^2$ additions).

#### 2. 1D Row Decomposition (Parallel)

Matrix $A$ is horizontally partitioned into row bands; $B$ is read-only shared. For $T$ threads, thread $t \in [0, T-1]$ receives:

$$\text{baseRows} = \lfloor N/T \rfloor, \quad \text{rem} = N \bmod T$$
$$\text{startRow}_t = t \cdot \text{baseRows} + \min(t, \text{rem})$$
$$\text{endRow}_t = \text{startRow}_t + \text{baseRows} + (t < \text{rem}\; ?\; 1 : 0)$$

Each thread writes **exclusively** to its disjoint row range in output $C$ → **zero lock contention, no `synchronized` blocks needed**.

#### 3. Performance Metrics

| Metric | Formula |
|---|---|
| Speedup $S(T)$ | $T_{\text{seq}} / T_{\text{par}}(T)$ |
| Parallel Efficiency $E(T)$ | $S(T) / T \times 100\%$ |
| Amdahl's Law upper bound | $S_{\text{max}} = 1 / [(1-f) + f/T]$ |

*For matrix multiply, parallel fraction $f \approx 0.95$ → theoretical max $S_{\text{max}} = 20\times$ as $T \to \infty$.*

---

<a name="exp-10-code-map"></a>
### Code Map & Package Architecture

| Component | Path | Responsibility |
|---|---|---|
| **Core Service** | `com.drs.comm.service.MatrixService` | Sequential baseline, `ExecutorService` row decomposition, remainder balancing, speedup computation, correctness verification, SSE publish |
| **Request DTO** | `com.drs.comm.model.MatrixRequest` | `n` (2–800), `threads` (1–32), `maxThreads` (sweep upper bound) |
| **Run Result** | `com.drs.comm.model.MatrixResult` | `sequentialMs`, `parallelMs`, `speedup`, `efficiency`, `correct`, `sampleResult[4][4]`, `steps[]` |
| **Sweep Result** | `com.drs.comm.model.MatrixSweepResult` | `runs[]`, `bestThreads`, `bestSpeedup`, `n`, `maxThreads` |
| **Controller** | `com.drs.comm.controller.MatrixController` | `POST /api/matrix/run`, `POST /api/matrix/sweep`, `GET /api/matrix/status`, `GET /api/matrix/latest` |
| **UI Page** | `static/pages/matrix.html` | Dimension slider (50–800), preset buttons, telemetry KPI cards, heatmap sample grid, Chart.js charts |
| **UI Script** | `static/js/matrix.js` | `runSingleBenchmark`, `runThreadSweep`, `renderSampleGrid` (heatmap), `updateSpeedupChart`, `updateLatencyChart`, mock fallback |
| **Tests** | `MatrixServiceTest`, `MatrixControllerTest` | Correctness vs sequential, non-divisible thread counts, sweep, validation, 400 responses |

---

<a name="exp-10-ui-demo"></a>
### UI Demonstration Walkthrough

> **URL:** `http://localhost:8080/pages/matrix.html` (or sidebar → **Parallel Matrix**)  
> **Quick offline test:** append `?mock=1`.

**Step 1 — Configure Parameters (20 sec)**  
- Drag the **Dimension Slider** to `400` (or click **"400 (Default)"** preset).  
  The label shows `400 × 400` = 160,000 cells, 64 M inner-loop operations.
- Select **4 Threads** from the Worker Threads dropdown.

**Step 2 — Run Single Benchmark**  
Click **"⚡ Run Benchmark"**. A loading spinner overlays the page during computation (~150 ms at $N=400$, 4 threads).

Upon completion:
- **Sequential Time** ≈ 145 ms (1-thread baseline)
- **Parallel Time** ≈ 42 ms (4-thread row decomposition)
- **Speedup** ≈ 3.45× · **Parallel Efficiency** ≈ 86%
- **Correctness Badge**: `🛡️ 100% MATCH (PASSED)` in green glow

**Step 3 — Inspect the Priority Tensor Sample**  
The 4×4 *Priority Tensor (Top-Left Sample)* card shows computed $C$ values with **heat-map coloration**: high values (red) = critical zones requiring immediate dispatch; low values (green) = lower priority areas.

**Step 4 — Execute Thread Sweep**  
Click **"📈 Sweep Threads (1..8)"**. The engine runs 4 sequential benchmarks ($T = 1, 2, 4, 8$).

Charts update live:
- **Speedup Line Chart**: empirical speedup (blue solid) vs ideal linear speedup (dashed grey). Observe the curve flatten as $T$ increases — matching Amdahl's Law.
- **Latency Bar Chart**: execution time drops sharply from 1T → 4T, then diminishes.

**Step 5 — Read the Execution Trace**  
The Chronological Step Trace terminal shows row decomposition math, barrier join timing, and final correctness confirmation at each thread count.

---

<a name="exp-10-viva"></a>
### Viva Examination Q&A — Experiment 10

#### Q1: Why 1D row decomposition rather than 2D block decomposition?

**Answer:**  
In a **shared-memory JVM**, Java arrays are laid out in **row-major order** — `A[i * N + k]` addresses are contiguous in memory per row. By assigning complete contiguous row segments to each thread:  
1. Each thread accesses sequential memory addresses → maximises **L1/L2 CPU cache line hits**.  
2. Two threads never write to the same cache line (rows are 64-byte-aligned at $N \geq 8$) → **zero false sharing**.  
While 2D block decomposition minimises inter-node communication volume in *distributed-memory* clusters (MPI across nodes), it creates strided memory access on shared-memory JVM → more cache misses → worse empirical performance. Row decomposition is strictly optimal here.

---

#### Q2: What causes parallel efficiency to drop below 100% as thread count increases?

**Answer:**  
Four compounding overheads:  
1. **Thread lifecycle overhead** — `ExecutorService.submit`, task scheduling, and `Future.get` barrier join incur fixed per-thread costs.  
2. **Memory bus saturation** — all $T$ threads concurrently read shared matrix $B$; the memory bus becomes the bottleneck beyond ~4 threads on most server CPUs.  
3. **Last-level cache pressure** — $3 \times N^2 \times 4$ bytes ≈ 1.92 MB at $N=400$ exceeds L1/L2 on most CPUs; main-memory access latency stalls all threads.  
4. **Hyperthreading diminishing returns** — when $T$ exceeds physical cores, logical threads share ALUs and FPUs; adding more threads adds scheduling overhead without adding computation capacity.

---

#### Q3: How does Amdahl's Law govern the theoretical maximum speedup?

**Answer:**  
Amdahl's Law states:
$$S_{\text{max}}(T) = \frac{1}{(1-f) + f/T}$$
For matrix multiplication, the parallel fraction $f \approx 0.95$ (the inner triple loop). The remaining 5% is sequential: matrix allocation, thread pool initialisation, and correctness verification. Even with $T \to \infty$:
$$S_{\text{max}} = \frac{1}{0.05} = 20\times$$
This proves that hardware parallelism alone cannot overcome sequential bottlenecks, and that optimising the sequential portion ($1-f$) often yields greater gains than adding more threads.

---

#### Q4: How is thread safety achieved in `MatrixService` without `synchronized` blocks?

**Answer:**  
Thread safety is achieved through **spatial data partitioning**:  
- Matrices $A$ and $B$ are **strictly read-only** after initialisation. Concurrent reads to the same address are safe on the JMM.  
- Matrix $C$ is partitioned: thread $t$ writes exclusively to indices $[\text{startRow}_t \times N,\; \text{endRow}_t \times N - 1]$.  

Because no two threads ever access the **same write address** in $C$, there are no write-write or read-write data hazards. No mutex locks, `volatile` wrappers, or `AtomicReference` are needed — eliminating contention entirely and achieving linear scaling up to the memory bus limit.

---

#### Q5: How is Experiment 10 connected to the disaster relief storyline in DRRCP?

**Answer:**  
Post-disaster zones form a geographic grid of sectors. In the DRRCP scenario:  
- **Matrix $A$ (Zone-Risk Tensor):** Each cell $A_{i,k}$ represents the combined hazard score for sector $i$ against disaster type $k$ (flood level, structural collapse probability, seismic intensity).  
- **Matrix $B$ (Resource Demand Tensor):** Each cell $B_{k,j}$ represents the resource urgency for hazard type $k$ requiring response type $j$ (medical kits, rescue personnel, rations per km²).  
- **Matrix $C = A \times B$ (Priority Score Matrix):** Cell $C_{i,j}$ gives the composite priority score for sector $i$ across resource type $j$. Higher scores → critical zones requiring immediate UAV dispatch.  

The parallel algorithm computes this on a 400×400 grid in ~42 ms (4 threads) vs ~145 ms sequentially — a 3.45× speedup that ensures the command dashboard produces actionable sector rankings in real time during an active disaster.

---

<a name="building"></a>
## Building & Verification Instructions

### 1. Prerequisites

```bash
# Verify Java 17
java -version          # must be 17.x

# Verify Maven
mvn -version           # must be 3.8+

# Verify the Spring Boot app compiles cleanly
mvn clean compile -q
```

### 2. Run Unit Tests for Experiments 9 & 10

```bash
# All Comm module tests (Exp 9 + Exp 10)
mvn test -Dtest="CommunicatorTest,CollectiveCommServiceTest,CollectiveCommControllerTest,MatrixServiceTest,MatrixControllerTest" -pl .

# Full project suite (122 tests expected — 0 failures)
mvn clean test
```

### 3. Start the Application

```bash
mvn spring-boot:run
# App listens on http://localhost:8080
# H2 console: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:file:./data/drs)
```

### 4. Verify REST Endpoints

```bash
# ── Experiment 9: Collective Communication ──

# Full simulation (Bcast + Scatter + Gather + self-check)
curl -s -X POST http://localhost:8080/api/comm/run-all \
  -H "Content-Type: application/json" \
  -d '{"numProcesses":4,"chunkSize":2,"rootRank":0,"alertText":"FLOOD ALERT"}' \
  | python -m json.tool

# Individual Bcast
curl -s -X POST http://localhost:8080/api/comm/bcast \
  -H "Content-Type: application/json" \
  -d '{"numProcesses":4,"chunkSize":2,"rootRank":0,"alertText":"EARTHQUAKE"}'

# Comm status
curl -s http://localhost:8080/api/comm/status

# ── Experiment 10: Parallel Matrix Multiplication ──

# Single benchmark (N=400, 4 threads)
curl -s -X POST http://localhost:8080/api/matrix/run \
  -H "Content-Type: application/json" \
  -d '{"n":400,"threads":4}' | python -m json.tool

# Thread sweep (N=400, up to 8 threads)
curl -s -X POST http://localhost:8080/api/matrix/sweep \
  -H "Content-Type: application/json" \
  -d '{"n":400,"maxThreads":8}' | python -m json.tool

# Matrix status
curl -s http://localhost:8080/api/matrix/status
```

### 5. Live SSE Event Stream

```bash
# Watch all live SSE events in real-time (Ctrl+C to stop)
curl -N http://localhost:8080/api/events

# Filter for comm module events only (PowerShell)
curl -N http://localhost:8080/api/events | Select-String '"module":"comm"'
```

### 6. Offline Mock Mode (No Server Required)

Append `?mock=1` to any page URL for instant UI testing without a running backend:  
- `http://localhost:8080/pages/comm.html?mock=1` — pre-loaded with 4-rank simulation  
- `http://localhost:8080/pages/matrix.html?mock=1` — pre-loaded with 400×400 sweep results

Mock data served from `static/mock/comm_result.json` and `static/mock/matrix_result.json`.

### 7. Verify Screenshots Directory

After a full scenario run, screenshots should be saved to `docs/screenshots/`:

```
docs/screenshots/
├── 01-dashboard-connected.png
├── 02-scenario-running.png
├── 03-comm-bcast.png
├── 04-comm-scatter.png
├── 05-comm-gather.png
├── 06-comm-selfcheck-passed.png
├── 07-matrix-benchmark.png
├── 08-matrix-sweep-chart.png
├── 09-matrix-heatmap.png
└── 10-about-page.png
```

---

<a name="api-reference"></a>
## REST API Quick Reference

### Experiment 9 — `/api/comm/`

| Method | Endpoint | Body | Response |
|---|---|---|---|
| `POST` | `/api/comm/bcast` | `{numProcesses, chunkSize, rootRank, alertText}` | `CommResult` with timeline and steps |
| `POST` | `/api/comm/scatter` | same | `CommResult` with scattered[] zones |
| `POST` | `/api/comm/gather` | same | `CommResult` with gathered[] reports and selfCheckPassed |
| `POST` | `/api/comm/run-all` | same | Full `CommResult` with all phases and self-check |
| `GET`  | `/api/comm/status` | — | Experiment metadata, bounds |

**`CommResult` fields:**

```json
{
  "operation": "RUN_ALL",
  "processes": 4,
  "rootRank": 0,
  "chunkSize": 2,
  "alertText": "CRITICAL: Sector 4 Flash Flood",
  "scattered": ["ZONE-A1", "ZONE-A2", ...],
  "gathered": ["REPORT[R0:ZONE-A1:EVAC_COMPLETE]", ...],
  "selfCheckPassed": true,
  "verified": true,
  "durationMs": 12,
  "timeline": [{"rank":0,"action":"BCAST_DISPATCHED","data":"...","order":1}, ...],
  "steps": ["=== STEP 1: BROADCAST ===", ...]
}
```

### Experiment 10 — `/api/matrix/`

| Method | Endpoint | Body | Response |
|---|---|---|---|
| `POST` | `/api/matrix/run` | `{n, threads}` | `MatrixResult` |
| `POST` | `/api/matrix/sweep` | `{n, maxThreads}` | `MatrixSweepResult` |
| `GET`  | `/api/matrix/status` | — | Experiment metadata |
| `GET`  | `/api/matrix/latest` | — | Most recent run result |

**`MatrixResult` fields:**

```json
{
  "n": 400,
  "threads": 4,
  "sequentialMs": 145.23,
  "parallelMs": 42.11,
  "speedup": 3.45,
  "efficiency": 0.86,
  "correct": true,
  "sampleResult": [[1400,1570,...], ...],
  "steps": ["Initialized 400x400 matrices...", ...],
  "completedAt": "2026-10-29T14:30:00.000Z"
}
```

**Validation Errors (HTTP 400):**
- `numProcesses` outside `[2, 16]` → `"Processes must be between 2 and 16"`
- `chunkSize` outside `[1, 10]` → `"Chunk size must be between 1 and 10"`
- Matrix `n` outside `[2, 800]` → `"Matrix dimension must be between 2 and 800"`
- `threads` outside `[1, 32]` → `"Thread count must be between 1 and 32"`
