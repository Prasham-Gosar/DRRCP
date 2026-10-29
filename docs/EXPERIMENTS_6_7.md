# Experiments 6 & 7 — Compute & Analytics Reference Guide

**Module Owner:** Parth (`com.drs.compute` & `analytics-spark`)  
**Project:** Disaster Relief & Rescue Coordination Platform (DRRCP) — Web Edition  
**Stack:** Java 17, Spring Boot 3.3.x, Apache Spark 3.5.3 (Subprocess), H2 Database, Chart.js, Server-Sent Events (SSE)

---

## Table of Contents
1. [Experiment 6: Distributed Task Load Balancing](#experiment-6-distributed-task-load-balancing)
   - [Aim & System Model](#exp-6-aim--system-model)
   - [Load Balancing Algorithm](#exp-6-algorithm)
   - [Code Map & Package Architecture](#exp-6-code-map)
   - [UI Demonstration Walkthrough](#exp-6-ui-demo-steps)
   - [Viva Examination Questions & Answers (5 Questions)](#exp-6-viva-qa)
2. [Experiment 7: MapReduce & Distributed Analytics](#experiment-7-mapreduce--distributed-analytics)
   - [Aim & System Model](#exp-7-aim--system-model)
   - [MapReduce Execution Phases](#exp-7-algorithm-phases)
   - [Subprocess Architecture & Mandatory Fallback](#exp-7-subprocess-architecture)
   - [Code Map & Module Layout](#exp-7-code-map)
   - [UI Demonstration Walkthrough](#exp-7-ui-demo-steps)
   - [Viva Examination Questions & Answers (5 Questions)](#exp-7-viva-qa)
3. [Building & Deployment Instructions](#building--deployment-instructions)

---

<a name="experiment-6-distributed-task-load-balancing"></a>
## Experiment 6: Distributed Task Load Balancing

<a name="exp-6-aim--system-model"></a>
### Aim & System Model
In a disaster recovery operation, unmanned aerial vehicles (UAVs) and edge micro-servers possess heterogeneous hardware limits, volatile battery levels, and fluctuating radio signal strength. The objective of Experiment 6 is to dynamically route incoming disaster rescue missions (search, medical triage, food rations) across the fleet to prevent individual node starvation or exhaustion while maximizing overall mission throughput.

<a name="exp-6-algorithm"></a>
### Load Balancing Algorithm

#### 1. Effective Capacity Formulation
A node's theoretical maximum capacity is scaled dynamically by its operational state (battery level and RF signal strength):

$$\text{Effective Capacity} = \text{maxCapacity} \times \left( \frac{\frac{\text{batteryLevel}}{100} + \frac{\text{signalStrength}}{100}}{2} \right)$$

*Example:* A UAV with `maxCapacity = 10.0`, `batteryLevel = 80%`, and `signal = 70%`:
$$\text{effectiveCapacity} = 10.0 \times \frac{0.80 + 0.70}{2} = 10.0 \times 0.75 = 7.50\text{ units}$$

#### 2. Candidate Filtering & Hardening
Before computing utilization:
- **Unavailable Nodes Skipped:** If `node.isAvailable() == false`, node is excluded immediately (`NodeTrace.skipped = true`).
- **Depleted Nodes Skipped:** If `effectiveCapacity <= 0.0` (zero battery or dead signal), node cannot take tasks and is excluded.

#### 3. Lowest Projected Utilization Selection
For every valid candidate node:
$$\text{projectedLoad} = \text{currentLoad} + \text{task.workload}$$
$$\text{projectedUtilization} = \frac{\text{projectedLoad}}{\text{effectiveCapacity}}$$

The scheduler selects candidate node $i$ having the lowest projected utilization:
$$i^* = \arg\min_{i} (\text{projectedUtilization}_i)$$

- **Tie-Breaking:** Evaluated using strict inequality (`<`). If two nodes produce identical projected utilization, the earlier evaluated candidate wins deterministically.
- **Sequential In-Memory State Updates:** When a batch or pending queue is assigned, each assigned task immediately increments the winning node's `currentLoad` so subsequent tasks reflect updated loads.

<a name="exp-6-code-map"></a>
### Code Map & Package Architecture

| Component | Class / Path | Responsibility |
|---|---|---|
| **Core Balancer** | `com.drs.compute.balancer.LoadBalancer` | Pure mathematical algorithm ported from legacy console code; calculates utilization, evaluates nodes, returns `AssignmentResult` |
| **Domain Model** | `com.drs.compute.model.LoadNode` | Node state: battery, signal, max capacity, effective capacity, current load, availability |
| **Task Model** | `com.drs.compute.model.RescueTask` & `RescueTaskEntity` | Task representation, workload weight, disaster type, target cluster, assigned node |
| **Trace Evaluation** | `com.drs.compute.model.NodeTrace` | Monospace audit trace per node: projected load, projected utilization, skip reason |
| **Service Layer** | `com.drs.compute.service.LoadBalancerService` | Manages candidate fleet (from `NodeEntityRepository` or stubs), batch assign-all, entity persistence, SSE events |
| **Partition Routing** | `com.drs.compute.service.ClusterTaskService` | Dispatches tasks via elected cluster leaders during network partitions (integration with Exp 4/5) |
| **REST Controller** | `com.drs.compute.controller.LoadBalancerController` | REST endpoints for fleet inspection, node availability toggle, reset, and batch assign-all |
| **Task API** | `com.drs.compute.controller.TaskController` | CRUD endpoints for rescue tasks, filtering, seeding, and scenario pipeline execution |
| **UI Dashboard** | `static/pages/loadbalancer.html` & `static/js/load-balancer.js` | Interactive fleet cards, animated utilization bars, node failure toggle, decision inspector, live SSE log |

<a name="exp-6-ui-demo-steps"></a>
### UI Demonstration Walkthrough
1. **Open Dashboard:** Navigate to `http://localhost:8080/pages/loadbalancer.html` (or click **Load Balancer** on the sidebar).
2. **Examine Fleet Cards:** Observe candidate nodes (`UAV-01`, `UAV-02`, `Edge-01`, `Edge-02`) with battery, signal, max capacity, and calculated effective capacity.
3. **Seed Disaster Tasks:** Click **"Seed Sample Tasks"** (or **"Load Samples"**). 12 tasks appear in the **Pending Queue** with varying workloads (1.0 to 7.0 units).
4. **Inspect Single Assignment:** Click **"Assign Now"** on `TASK-01` (workload 3.0). The **Decision Inspector** expands, showing each node's projected utilization and highlighting the winner.
5. **Simulate Node Failure:** On `UAV-01`, toggle **"Mark Unavailable"**. The badge updates to red `UNAVAILABLE`.
6. **Trigger Batch Balancing:** Click **"⚡ Assign All Pending"**. Observe that all remaining pending tasks route strictly to available nodes (`Edge-01`, `Edge-02`, `UAV-02`). In the Decision Inspector, `UAV-01` is marked `[SKIPPED: UAV-01 is unavailable]`.
7. **Verify Real-Time Telemetry:** Progress bars dynamically animate from green (<50%) to amber (50–80%) to red (>80%). Live Server-Sent Events stream into the **Live Event Feed** (`TASK_ASSIGNED`).

<a name="exp-6-viva-qa"></a>
### Viva Examination Questions & Answers (5 Questions)

#### Q1: What is "Effective Capacity" and why do we weight battery level and signal strength equally?
**Answer:** In remote disaster zones, raw compute capability (e.g., CPU frequency or storage capacity) is meaningless if a mobile UAV runs out of battery mid-flight or loses radio contact with the command station. Effective capacity models real-world survivability:
$$\text{effectiveCapacity} = \text{maxCapacity} \times \frac{(\text{battery}/100 + \text{signal}/100)}{2}$$
Both metrics are normalized to $[0.0, 1.0]$. A 15-unit edge server with 90% battery and 85% signal yields an effective capacity of $13.125$, whereas an 80% battery/70% signal UAV with 10-unit max capacity yields $7.50$.

#### Q2: Why does a completely idle node ($\text{currentLoad} = 0.0$) not always win the task assignment?
**Answer:** The algorithm minimizes **projected utilization** ($\frac{\text{currentLoad} + \text{workload}}{\text{effectiveCapacity}}$), not absolute load. If Node A has $\text{currentLoad} = 0.0$ and $\text{effectiveCapacity} = 2.0$, a task of workload $3.0$ produces a projected utilization of $\frac{3.0}{2.0} = 1.50$ ($150\%$). If Node B has $\text{currentLoad} = 4.0$ and $\text{effectiveCapacity} = 13.125$, the same task produces $\frac{7.0}{13.125} = 0.533$ ($53.3\%$). Node B wins despite already carrying load because its effective capacity is substantially higher.

#### Q3: What is the computational time and space complexity of assigning a single task?
**Answer:** For a candidate fleet of $N$ nodes:
- **Time Complexity:** $O(N)$ linear scan. We evaluate effective capacity, availability check, and division once per node.
- **Space Complexity:** $O(N)$ auxiliary memory to construct the evaluation traces (`NodeTrace`) returned to the caller for inspection.

#### Q4: How does the load balancer resolve ties when two nodes yield identical projected utilization?
**Answer:** The algorithm uses strict inequality (`if (projectedUtilization < lowestUtilization)`). If candidate node $A$ and candidate node $B$ have identical projected utilization, the first node evaluated retains the winning position. This guarantees deterministic behavior across distributed runs.

#### Q5: What happens when all candidate nodes are unavailable or depleted?
**Answer:** The system handles this gracefully without throwing an uncaught exception:
1. Every candidate node evaluates to skipped (due to `!isAvailable()` or `effectiveCapacity <= 0`).
2. `selectedNode` remains `null`.
3. The balancer returns `AssignmentResult` with `success = false`, `chosenNodeId = null`, and an explicit step entry: `"No available node found for this task."`.
4. In `assignAllPending()`, unassignable tasks remain safely in `PENDING` status in H2 database for future retries.

---

<a name="experiment-7-mapreduce--distributed-analytics"></a>
## Experiment 7: MapReduce & Distributed Analytics

<a name="exp-7-aim--system-model"></a>
### Aim & System Model
After disaster rescue tasks are logged and dispatched across geographic zones, coordinators require macro-level analytics: total relief workload per cluster, distribution of disaster types (medical emergencies vs. food drops vs. structural search), and bottleneck partition detection. Experiment 7 implements this aggregation using the distributed **MapReduce programming model** with dual execution engines: **Apache Spark** (standalone subprocess) and a mandatory in-memory **Java Streams Fallback**.

<a name="exp-7-algorithm-phases"></a>
### MapReduce Execution Phases

```
Input Tasks (H2 Database / Text File)
                  │
                  ▼
┌────────────────────────────────────────────────────────┐
│ 1. MAP PHASE                                           │
│ Input:  RescueTask(cluster, type, workload)            │
│ Emit:   Key = "cluster|type"                           │
│         Value = (count = 1, workload = task.workload)  │
└────────────────────────────────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────────────┐
│ 2. SHUFFLE & SORT PHASE                                │
│ Hash/sort partition keys across nodes                  │
│ Group:  <Key, List<(count, workload)>>                 │
└────────────────────────────────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────────────┐
│ 3. REDUCE PHASE                                        │
│ Associative & commutative aggregation:                 │
│   reducedCount    = sum(counts)                        │
│   reducedWorkload = sum(workloads)                     │
└────────────────────────────────────────────────────────┘
                  │
                  ▼
┌────────────────────────────────────────────────────────┐
│ 4. ROLLUP & MARGINALS                                  │
│ Workload per cluster, tasks per disaster type,         │
│ and identification of Busiest Bottleneck Cluster       │
└────────────────────────────────────────────────────────┘
```

#### 1. MAP Phase
Each record is transformed into an intermediate key-value pair:
- **Intermediate Key:** Composite partition key `cluster|taskType` (e.g. `Cluster-A|Medical`).
- **Intermediate Value:** Partial aggregate tuple `(count = 1, workload = task.workload)`.
*Distributed Property:* Mappers run embarrassingly in parallel with zero inter-node communication.

#### 2. SHUFFLE & SORT Phase
The system routes all intermediate pairs sharing the exact same composite key to the same reducer bucket. In distributed Spark/Hadoop, this causes network I/O across cluster workers; in `MapReduceFallbackService`, it is implemented via `Collectors.groupingBy()` with key sorting in a `TreeMap`.

#### 3. REDUCE Phase
A binary associative and commutative reduction operator aggregates each partition bucket:
$$\text{reducedCount} = \sum \text{count}_i, \quad \text{reducedWorkload} = \sum \text{workload}_i$$

#### 4. Marginal Rollup
A secondary rollup over the reduced buckets computes:
- Workload per cluster ($\sum_{\text{type}} \text{workload}$)
- Task frequency per disaster type ($\sum_{\text{cluster}} \text{count}$)
- Busiest cluster metric: $\max_{\text{cluster}} (\text{workload}_{\text{cluster}})$

<a name="exp-7-subprocess-architecture"></a>
### Subprocess Architecture & Mandatory Fallback

To prevent Apache Spark 3.5.3's heavy transitive dependencies (Scala, Netty, Jackson 2.15, Jetty) from polluting Spring Boot 3.3's classpath, the Spark job is housed in a standalone Maven module `analytics-spark/`.

```
┌──────────────────────────────────────┐
│        Spring Boot 3.3 App           │
│         (com.drs.compute)            │
│                                      │
│   SparkJobService                    │
│   ┌──────────────────────────────┐   │
│   │ Check if Spark JAR exists?   │   │
│   └──────────────┬───────────────┘   │
│                  │                   │
│          Yes     │    No / Error     │
│       ┌──────────┴─────────┐         │
│       ▼                    ▼         │
│  ProcessBuilder       MapReduce-     │
│  java -jar            FallbackService│
│  analytics-spark.jar  (Java Streams) │
└───────┬────────────────────┬─────────┘
        │                    │
        ▼                    ▼
   External OS          Instant In-Memory
   JVM Subprocess       Zero-Dependency Result
```

- **Execution Flow:** `SparkJobService` exports active tasks to a temporary text file (`cluster,type,workload`), prepares Java 17 `--add-opens` flags, and starts a subprocess via `ProcessBuilder`.
- **Automatic Fallback:** If the Spark JAR is missing, the subprocess times out (>40s), or an execution exception occurs, the service automatically invokes `MapReduceFallbackService`, sets `fallbackUsed = true`, tags `engine = "streams-fallback"`, and appends clear diagnostics in the step trace.
- **Concurrency Guard:** An `AtomicBoolean analyticsRunning` prevents multiple concurrent executions; subsequent parallel triggers receive HTTP 409 Conflict.

<a name="exp-7-code-map"></a>
### Code Map & Module Layout

| Component | Path | Responsibility |
|---|---|---|
| **Spark Submodule** | `analytics-spark/pom.xml` | Standalone Maven module with Spark 3.5.3 and shaded fat JAR build |
| **Spark Main Job** | `analytics-spark/.../SparkAnalyticsJob.java` | Spark `JavaSparkContext` MapReduce job writing JSON output |
| **Java Fallback Service**| `com.drs.compute.service.MapReduceFallbackService` | Pure Java 17 Streams in-memory MapReduce engine with identical output schema |
| **Spark Coordinator** | `com.drs.compute.service.SparkJobService` | Subprocess orchestration, JAR detection, fallback trigger, and run persistence |
| **Run Entity** | `com.drs.compute.model.AnalyticsRun` | H2 JPA entity persisting execution engine, duration, and output JSON |
| **Run Repository** | `com.drs.compute.repository.AnalyticsRunRepository` | H2 queries `findAllByOrderByTimestampDesc()` and latest run query |
| **REST Controller** | `com.drs.compute.controller.AnalyticsController` | REST endpoints `POST /run`, `GET /latest`, `GET /history`, `POST /seed` |
| **UI Dashboard** | `static/pages/analytics.html` & `static/js/analytics.js` | Dual Chart.js charts, KPI cards, filter panel, paginated table, leader badges |

<a name="exp-7-ui-demo-steps"></a>
### UI Demonstration Walkthrough
1. **Open Analytics Dashboard:** Navigate to `http://localhost:8080/pages/analytics.html`.
2. **Execute Streams Engine:**
   - Select **"Java Streams (In-Memory Fallback)"** in the engine dropdown.
   - Click **"⚡ Run MapReduce"**.
   - Watch the monospaced terminal output stream: `[INIT]`, `[MAP] Emitting intermediate pairs`, `[SHUFFLE/SORT] Grouping keys`, `[REDUCE] Aggregating composite key buckets`.
3. **Inspect Visualizations:**
   - **Cluster Workload Bar Chart:** Visualizes workload and task count per cluster.
   - **Tasks-Over-Time Progression:** Shows cumulative workload arrival curve.
   - **Busiest Cluster KPI:** Highlights the primary bottleneck partition (e.g., `Cluster-A (18.0 u)`).
4. **Demonstrate Spark Fallback:**
   - Select **"Apache Spark (Subprocess)"** in the dropdown.
   - Click **"⚡ Run MapReduce"**.
   - If the Spark fat JAR is not pre-built, observe that the engine badge automatically switches to amber **`STREAMS (FALLBACK)`** and the terminal logs: `[WARN] Spark fat JAR not found in analytics-spark/target. Activating mandatory Streams fallback.`
5. **Demonstrate Network Partition Pipeline (Exp 6 + 7 Integration):**
   - Click **"🚀 Run Partitioned Pipeline"**.
   - Disaster tasks are generated across Cluster-A and Cluster-B during simulated network isolation.
   - Grasit's elected leaders (`👑 UAV-02` for Cluster-A and `👑 Edge-01` for Cluster-B) dispatch tasks to local nodes.
   - Click the node pill badge on any task to jump directly to its allocation in the Load Balancer view.

<a name="exp-7-viva-qa"></a>
### Viva Examination Questions & Answers (5 Questions)

#### Q1: What is the core philosophy of the MapReduce programming paradigm?
**Answer:** MapReduce is designed for distributed, data-parallel computation across clusters of commodity hardware. It separates computation into two user-defined functions:
1. $\text{Map}(k_1, v_1) \to \text{list}(k_2, v_2)$: Transforms input splits into intermediate key-value pairs without dependency on other records.
2. $\text{Reduce}(k_2, \text{list}(v_2)) \to \text{list}(v_3)$: Aggregates all intermediate values sharing the same intermediate key $k_2$.
The underlying framework automatically handles data partitioning, scheduling, inter-node shuffle data transfer, and fault tolerance.

#### Q2: What happens during the "Shuffle & Sort" phase and why is it considered the most expensive phase in distributed systems?
**Answer:** Between the Map and Reduce phases, the system must collect all intermediate pairs emitted across all mapper nodes and partition them so that every intermediate value for key $K$ arrives at the exact reducer assigned to key $K$. This requires:
- Sorting intermediate pairs by key locally on mapper nodes.
- Partitioning across the network via hash partitioning (`hash(key) % numReducers`).
- Transferring large volumes of data across the network (cross-rack I/O).
Because it involves disk serialization, network transfer, and external merge-sorting, Shuffle & Sort is typically the bottleneck in distributed data pipelines.

#### Q3: Why must the reduction operator be associative and commutative?
**Answer:** In a distributed cluster:
- **Associativity:** $(a \oplus b) \oplus c = a \oplus (b \oplus c)$. This allows the framework to execute intermediate aggregations (**Combiners**) locally on mapper nodes prior to sending data across the network, reducing network traffic.
- **Commutativity:** $a \oplus b = b \oplus a$. This allows the reducer to process input records in any arrival order without affecting the final mathematical outcome.
For task counting and workload summation, addition is strictly associative and commutative.

#### Q4: Why did we run Apache Spark as an external subprocess instead of embedding it directly in the Spring Boot Maven POM?
**Answer:** Embedding Apache Spark 3.5.3 directly into a Spring Boot 3.3 application causes severe dependency and classloader collisions:
1. **Scala Library Version Conflicts:** Spark pulls Scala 2.12/2.13 runtime binaries.
2. **Netty and Jackson Collisions:** Spring Boot 3.3 uses Netty 4.1.x and Jackson 2.17+; Spark bundles shaded Netty and older Jackson versions that break Spring Web MVC.
3. **JVM Module Encapsulation:** Spark uses internal Java unsafe memory APIs that require extensive `--add-opens` flags incompatible with standard Spring Boot fat JAR launchers.
By isolating Spark into `analytics-spark/` and running it as a managed OS subprocess via `ProcessBuilder`, the web application remains clean, lightweight, and robust.

#### Q5: How does the system ensure zero downtime when the Spark runtime fails or the JAR is absent?
**Answer:** The architecture enforces a **Mandatory Degradation Path**:
`SparkJobService` checks for the existence of `analytics-spark-1.0.0-SNAPSHOT.jar` and catches any subprocess exceptions (timeout, non-zero exit code, unreadable output). If any issue occurs, it delegates directly to `MapReduceFallbackService`, which executes the identical Map, Shuffle/Sort, and Reduce aggregation in-memory using Java 17 Streams. The response JSON adheres to the exact same schema, with `fallbackUsed = true` so the UI informs the user without failing the request.

---

<a name="building--deployment-instructions"></a>
## Building & Deployment Instructions

### 1. Build the Main Web Application
From the repository root:
```bash
mvn clean compile
```

### 2. Build the Standalone Spark Fat JAR (Exp 7)
To enable the native Apache Spark subprocess engine:
```bash
cd analytics-spark
mvn clean package -DskipTests
cd ..
```
This produces `analytics-spark/target/analytics-spark-1.0.0-SNAPSHOT.jar`.

### 3. Run the Spring Boot Web Server
```bash
mvn spring-boot:run
```
The web dashboard is accessible at:
- **Main Portal:** `http://localhost:8080/`
- **Load Balancer (Exp 6):** `http://localhost:8080/pages/loadbalancer.html`
- **MapReduce Analytics (Exp 7):** `http://localhost:8080/pages/analytics.html`
- **H2 Web Console:** `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/drsdb`)

### 4. Configuration Properties (`application.properties`)
```properties
# Compute & Analytics Configuration
drs.analytics.engine=streams
drs.analytics.spark.jar-path=analytics-spark/target/analytics-spark-1.0.0-SNAPSHOT.jar
```
To set Spark as the default engine across runs, set `drs.analytics.engine=spark`.
