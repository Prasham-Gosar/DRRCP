# Analytics Spark Module (Experiment 7)

Standalone Apache Spark 3.5.3 MapReduce module owned by **Parth** (`com.drs.compute` / `analytics-spark`).

> **Architectural Note:** This module is completely separate from the root Spring Boot application POM to prevent Spark dependency bloat and classpath conflicts in Spring Boot 3.3.x. The main web application triggers this job as a background subprocess.

---

## 1. Prerequisites
- **JDK 17+**
- **Apache Maven 3.8+**

---

## 2. Java 17 Compatibility (`--add-opens`)
Apache Spark 3.5.3 accesses certain JVM internal memory and reflection structures that Java 17 encapsulates by default. When running standalone or from command line with Java 17, the following JVM options are required:

```bash
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/sun.nio.cs=ALL-UNNAMED
--add-opens=java.base/sun.security.action=ALL-UNNAMED
--add-opens=java.base/sun.util.calendar=ALL-UNNAMED
--add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED
```

These flags are already configured inside `analytics-spark/pom.xml` under `maven-surefire-plugin` and `exec-maven-plugin`.

---

## 3. How to Build & Package

From the module directory:

```bash
cd analytics-spark
mvn clean package
```

This compiles the code and generates a shaded fat JAR: `target/analytics-spark-1.0.0-SNAPSHOT.jar`.

---

## 4. How to Run Standalone

### Option A: Via Maven Exec Plugin (Easiest)
```bash
cd analytics-spark
mvn exec:exec
```

### Option B: Direct Java invocation with JAR
```bash
cd analytics-spark
java --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
     --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens=java.base/java.io=ALL-UNNAMED \
     --add-opens=java.base/java.net=ALL-UNNAMED \
     --add-opens=java.base/java.nio=ALL-UNNAMED \
     --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
     --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
     --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
     --add-opens=java.base/sun.nio.cs=ALL-UNNAMED \
     --add-opens=java.base/sun.security.action=ALL-UNNAMED \
     --add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
     --add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED \
     -jar target/analytics-spark-1.0.0-SNAPSHOT.jar input/rescue_tasks.txt output/analytics_result.json
```

*(On Windows PowerShell, replace `\` with backtick `` ` `` or put arguments on one line).*

---

## 5. Input Format (`input/rescue_tasks.txt`)
Each line consists of comma-separated values:
`ClusterId,TaskType,Workload`

Example:
```text
ClusterA,Medical,5
ClusterA,Food,3
ClusterB,Medical,4
ClusterB,Search,6
ClusterA,Medical,2
ClusterB,Food,5
ClusterC,Search,7
ClusterC,Medical,3
ClusterA,Search,4
ClusterC,Food,2
```

---

## 6. Output Format (`output/analytics_result.json`)
The job writes a structured JSON document containing totals, per-cluster counts/workload, per-type counts/workload, and detailed breakdown:

```json
{
  "appName": "DRS-SparkAnalytics",
  "timestamp": "2026-10-26T23:30:00Z",
  "inputFile": "input/rescue_tasks.txt",
  "totalTasks": 10,
  "totalWorkload": 41.0,
  "countsPerCluster": {
    "ClusterA": 4,
    "ClusterB": 3,
    "ClusterC": 3
  },
  "workloadPerCluster": {
    "ClusterA": 14.0,
    "ClusterB": 15.0,
    "ClusterC": 12.0
  },
  "countsPerType": {
    "Food": 3,
    "Medical": 4,
    "Search": 3
  },
  "workloadPerType": {
    "Food": 10.0,
    "Medical": 14.0,
    "Search": 17.0
  },
  "breakdown": [
    { "cluster": "ClusterA", "taskType": "Food", "taskCount": 1, "totalWorkload": 3.0 },
    { "cluster": "ClusterA", "taskType": "Medical", "taskCount": 2, "totalWorkload": 7.0 },
    { "cluster": "ClusterA", "taskType": "Search", "taskCount": 1, "totalWorkload": 4.0 },
    { "cluster": "ClusterB", "taskType": "Food", "taskCount": 1, "totalWorkload": 5.0 },
    { "cluster": "ClusterB", "taskType": "Medical", "taskCount": 1, "totalWorkload": 4.0 },
    { "cluster": "ClusterB", "taskType": "Search", "taskCount": 1, "totalWorkload": 6.0 },
    { "cluster": "ClusterC", "taskType": "Food", "taskCount": 1, "totalWorkload": 2.0 },
    { "cluster": "ClusterC", "taskType": "Medical", "taskCount": 1, "totalWorkload": 3.0 },
    { "cluster": "ClusterC", "taskType": "Search", "taskCount": 1, "totalWorkload": 7.0 }
  ]
}
```
