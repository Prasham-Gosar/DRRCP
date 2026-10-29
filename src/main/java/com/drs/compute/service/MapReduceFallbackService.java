package com.drs.compute.service;

import com.drs.compute.model.AnalyticsBreakdownItem;
import com.drs.compute.model.AnalyticsResult;
import com.drs.compute.model.RescueTaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-Memory MapReduce Engine implemented using Java 17 Streams (Exp 7).
 * <p>
 * Serves as an algorithmically faithful fallback when Apache Spark is unavailable,
 * disabled via configuration {@code drs.analytics.engine=streams}, or during local development
 * on machines without a full Spark runtime environment.
 * <p>
 * <b>Distributed Computing Viva Notes (Exp 7 Correspondence):</b>
 * <ul>
 *   <li><b>1. MAP PHASE:</b>
 *     Each record {@code (cluster, type, workload)} is mapped to an intermediate key-value pair.
 *     The intermediate Key is the composite key {@code "cluster|taskType"} (e.g. {@code "ClusterA|Medical"}).
 *     The intermediate Value is a partial aggregate record {@code (count = 1, workload = task.workload)}.
 *     In distributed computing, mappers operate independently and embarrassingly parallel on data splits.
 *   </li>
 *   <li><b>2. SHUFFLE &amp; SORT PHASE:</b>
 *     Intermediate keys are partitioned and sorted. Values with identical keys are grouped together
 *     forming {@code <Key, List<IntermediateValue>>}. In Spark/Hadoop, this causes network I/O across partitions.
 *     In Java Streams, this is accomplished via {@code Collectors.groupingBy()} with {@code TreeMap} key sorting.
 *   </li>
 *   <li><b>3. REDUCE PHASE:</b>
 *     The associative and commutative reduction operator aggregates the grouped values:
 *     {@code reducedCount = sum(counts)} and {@code reducedWorkload = sum(workloads)}.
 *     The output is a materialized summary per {@code (cluster, taskType)}.
 *   </li>
 *   <li><b>4. COMBINER / ROLLUP:</b>
 *     Marginal distributions (total workload per cluster, task frequency per disaster type)
 *     are computed by secondary rollup across the reduced composite keys.
 *   </li>
 * </ul>
 */
@Service
public class MapReduceFallbackService {

    private static final Logger log = LoggerFactory.getLogger(MapReduceFallbackService.class);

    /**
     * Intermediate pair emitted during the MAP phase.
     */
    public record IntermediatePair(String compositeKey, int count, double workload, String cluster, String taskType) {}

    /**
     * Execute the in-memory MapReduce computation over the provided tasks.
     *
     * @param tasks             list of rescue tasks to analyze
     * @param sourceDescription origin of input data (e.g. "H2 Database (rescue_tasks)")
     * @param elapsedMs         processing duration to report if pre-measured, or -1 to measure locally
     * @param isFallback        true if called as an automatic fallback after Spark failure
     * @return structured {@link AnalyticsResult} matching Spark JSON schema
     */
    public AnalyticsResult computeAnalytics(List<RescueTaskEntity> tasks,
                                            String sourceDescription,
                                            long elapsedMs,
                                            boolean isFallback) {
        long startTime = System.currentTimeMillis();
        List<String> steps = new ArrayList<>();
        String engineName = isFallback ? "streams-fallback" : "streams";

        steps.add(String.format("[INIT] Starting MapReduce computation via %s on %d tasks",
                engineName.toUpperCase(), tasks != null ? tasks.size() : 0));

        if (tasks == null || tasks.isEmpty()) {
            steps.add("[WARN] No input tasks provided; returning empty analytics result");
            return new AnalyticsResult(
                    "DRS-MapReduceStreams",
                    Instant.now().toString(),
                    sourceDescription != null ? sourceDescription : "rescue_tasks (empty)",
                    0, 0.0,
                    Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyList(),
                    engineName,
                    elapsedMs >= 0 ? elapsedMs : (System.currentTimeMillis() - startTime),
                    isFallback,
                    steps
            );
        }

        /*
         * =========================================================================
         * PHASE 1: MAP PHASE
         * Transform each RescueTaskEntity line into an IntermediatePair:
         * Key:   cluster + "|" + taskType
         * Value: count = 1, workload = task.getWorkload()
         * =========================================================================
         */
        steps.add(String.format("[MAP] Emitting intermediate key-value pairs for %d input records...", tasks.size()));

        List<IntermediatePair> mappedPairs = tasks.stream()
                .filter(Objects::nonNull)
                .map(task -> {
                    String cluster = (task.getClusterId() != null && !task.getClusterId().isBlank())
                            ? task.getClusterId().trim() : "Unknown-Cluster";
                    String type = (task.getType() != null && !task.getType().isBlank())
                            ? task.getType().trim() : "General";
                    double workload = Math.max(0.0, task.getWorkload());
                    String compositeKey = cluster + "|" + type;
                    return new IntermediatePair(compositeKey, 1, workload, cluster, type);
                })
                .collect(Collectors.toList());

        for (IntermediatePair pair : mappedPairs) {
            steps.add(String.format("  -> MAP EMIT: Key='%s' => Value=(count=1, workload=%.1f)",
                    pair.compositeKey(), pair.workload()));
        }

        /*
         * =========================================================================
         * PHASE 2: SHUFFLE & SORT
         * Group intermediate values by compositeKey and sort them into a TreeMap.
         * =========================================================================
         */
        steps.add(String.format("[SHUFFLE/SORT] Grouping %d intermediate pairs across partition keys...", mappedPairs.size()));

        Map<String, List<IntermediatePair>> groupedByKey = mappedPairs.stream()
                .collect(Collectors.groupingBy(
                        IntermediatePair::compositeKey,
                        TreeMap::new,
                        Collectors.toList()
                ));

        /*
         * =========================================================================
         * PHASE 3: REDUCE PHASE
         * Aggregate (sum counts and sum workload) for each distinct compositeKey.
         * =========================================================================
         */
        steps.add(String.format("[REDUCE] Aggregating %d distinct composite key buckets...", groupedByKey.size()));

        int totalTasks = 0;
        double totalWorkload = 0.0;
        Map<String, Integer> countsPerCluster = new TreeMap<>();
        Map<String, Double> workloadPerCluster = new TreeMap<>();
        Map<String, Integer> countsPerType = new TreeMap<>();
        Map<String, Double> workloadPerType = new TreeMap<>();
        List<AnalyticsBreakdownItem> breakdown = new ArrayList<>();

        for (Map.Entry<String, List<IntermediatePair>> entry : groupedByKey.entrySet()) {
            String compositeKey = entry.getKey();
            List<IntermediatePair> bucket = entry.getValue();

            String cluster = bucket.get(0).cluster();
            String taskType = bucket.get(0).taskType();

            // Associative sum reduction:
            int reducedCount = bucket.stream().mapToInt(IntermediatePair::count).sum();
            double reducedWorkload = bucket.stream().mapToDouble(IntermediatePair::workload).sum();

            totalTasks += reducedCount;
            totalWorkload += reducedWorkload;

            countsPerCluster.merge(cluster, reducedCount, Integer::sum);
            workloadPerCluster.merge(cluster, reducedWorkload, Double::sum);

            countsPerType.merge(taskType, reducedCount, Integer::sum);
            workloadPerType.merge(taskType, reducedWorkload, Double::sum);

            breakdown.add(new AnalyticsBreakdownItem(cluster, taskType, reducedCount, reducedWorkload));

            steps.add(String.format("  -> REDUCE BUCKET '%s': tasks=%d, totalWorkload=%.1f",
                    compositeKey, reducedCount, reducedWorkload));
        }

        long actualDuration = elapsedMs >= 0 ? elapsedMs : (System.currentTimeMillis() - startTime);
        steps.add(String.format("[COMPLETE] MapReduce finished in %d ms: totalTasks=%d, totalWorkload=%.1f across %d clusters",
                actualDuration, totalTasks, totalWorkload, countsPerCluster.size()));

        return new AnalyticsResult(
                "DRS-MapReduceStreams",
                Instant.now().toString(),
                sourceDescription != null ? sourceDescription : "rescue_tasks (H2)",
                totalTasks,
                totalWorkload,
                countsPerCluster,
                workloadPerCluster,
                countsPerType,
                workloadPerType,
                breakdown,
                engineName,
                actualDuration,
                isFallback,
                steps
        );
    }
}

