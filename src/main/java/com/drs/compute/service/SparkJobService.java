package com.drs.compute.service;

import com.drs.compute.model.AnalyticsResult;
import com.drs.compute.model.AnalyticsRun;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.repository.AnalyticsRunRepository;
import com.drs.compute.repository.RescueTaskRepository;
import com.drs.core.event.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service managing distributed MapReduce execution via Apache Spark subprocess
 * and coordinating the mandatory in-memory Java Streams fallback (Exp 7).
 */
@Service
public class SparkJobService {

    private static final Logger log = LoggerFactory.getLogger(SparkJobService.class);

    private final RescueTaskRepository taskRepository;
    private final AnalyticsRunRepository runRepository;
    private final MapReduceFallbackService fallbackService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper mapper;

    @Value("${drs.analytics.engine:streams}")
    private String defaultEngine;

    @Value("${drs.analytics.spark.jar-path:analytics-spark/target/analytics-spark-1.0.0-SNAPSHOT.jar}")
    private String configuredJarPath;

    @Autowired
    public SparkJobService(RescueTaskRepository taskRepository,
                           AnalyticsRunRepository runRepository,
                           MapReduceFallbackService fallbackService,
                           @Autowired(required = false) EventPublisher eventPublisher,
                           ObjectMapper mapper) {
        this.taskRepository = taskRepository;
        this.runRepository = runRepository;
        this.fallbackService = fallbackService;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
    }

    /**
     * Executes MapReduce analytics asynchronously using CompletableFuture.
     *
     * @param requestedEngine optional engine override ("spark" or "streams")
     * @return CompletableFuture completing with {@link AnalyticsResult}
     */
    public CompletableFuture<AnalyticsResult> runAnalyticsAsync(String requestedEngine) {
        return CompletableFuture.supplyAsync(() -> runAnalytics(requestedEngine));
    }

    /**
     * Executes MapReduce analytics with automatic fallback and persistence.
     *
     * @param requestedEngine optional engine override ("spark" or "streams")
     * @return structured {@link AnalyticsResult}
     */
    public AnalyticsResult runAnalytics(String requestedEngine) {
        long overallStart = System.currentTimeMillis();
        String engine = (requestedEngine != null && !requestedEngine.isBlank())
                ? requestedEngine.trim().toLowerCase()
                : defaultEngine.toLowerCase();

        // 1. Retrieve current tasks or seed defaults if database is empty
        List<RescueTaskEntity> tasks = getOrSeedTasks();

        // 2. Publish ANALYTICS_STARTED event
        if (eventPublisher != null) {
            Map<String, Object> startPayload = new LinkedHashMap<>();
            startPayload.put("engine", engine);
            startPayload.put("taskCount", tasks.size());
            startPayload.put("timestamp", Instant.now().toString());
            eventPublisher.publish("compute", "ANALYTICS_STARTED", startPayload);
        }

        AnalyticsResult result;

        // 3. Execute requested engine
        if ("spark".equalsIgnoreCase(engine)) {
            result = attemptSparkExecution(tasks, overallStart);
        } else {
            result = fallbackService.computeAnalytics(
                    tasks,
                    "In-Memory Java Streams Engine (rescue_tasks)",
                    -1,
                    false
            );
        }

        // 4. Persist run to database
        persistRun(result);

        // 5. Publish ANALYTICS_DONE event
        if (eventPublisher != null) {
            eventPublisher.publish("compute", "ANALYTICS_DONE", result);
        }

        return result;
    }

    /**
     * Attempts to run Apache Spark via ProcessBuilder subprocess.
     * Falls back seamlessly to {@link MapReduceFallbackService} if Spark fails or JAR is missing.
     */
    private AnalyticsResult attemptSparkExecution(List<RescueTaskEntity> tasks, long overallStart) {
        List<String> sparkSteps = new ArrayList<>();
        sparkSteps.add("[SPARK] Initiating Apache Spark MapReduce subprocess (Exp 7)...");

        File sparkJar = locateSparkJar();
        if (sparkJar == null || !sparkJar.exists()) {
            log.warn("Spark JAR not found at candidate locations. Falling back to Streams engine.");
            sparkSteps.add("[WARN] Spark fat JAR not found in analytics-spark/target. Activating mandatory Streams fallback.");
            AnalyticsResult fallbackResult = fallbackService.computeAnalytics(
                    tasks,
                    "Java Streams Fallback (Spark JAR missing)",
                    System.currentTimeMillis() - overallStart,
                    true
            );
            List<String> combined = new ArrayList<>(sparkSteps);
            combined.addAll(fallbackResult.getSteps());
            fallbackResult.setSteps(combined);
            return fallbackResult;
        }

        Path tempInput = null;
        Path tempOutput = null;

        try {
            // Export tasks to temporary file in 'cluster,type,workload' format
            tempInput = Files.createTempFile("rescue_tasks_input_", ".txt");
            tempOutput = Files.createTempFile("analytics_result_output_", ".json");

            List<String> taskLines = new ArrayList<>();
            for (RescueTaskEntity task : tasks) {
                String cluster = (task.getClusterId() != null) ? task.getClusterId().trim() : "Cluster-A";
                String type = (task.getType() != null) ? task.getType().trim() : "General";
                double workload = Math.max(0.0, task.getWorkload());
                taskLines.add(String.format(Locale.ROOT, "%s,%s,%.2f", cluster, type, workload));
            }
            Files.write(tempInput, taskLines);

            sparkSteps.add(String.format("[SPARK] Exported %d tasks to temp input file: %s",
                    tasks.size(), tempInput.getFileName()));

            // Build subprocess command with required Java 17 add-opens flags
            List<String> command = buildSparkCommand(sparkJar.getAbsolutePath(),
                    tempInput.toAbsolutePath().toString(),
                    tempOutput.toAbsolutePath().toString());

            sparkSteps.add("[SPARK] Launching Java process with JVM add-opens flags...");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            long procStart = System.currentTimeMillis();
            Process process = pb.start();

            boolean finished = process.waitFor(40, TimeUnit.SECONDS);
            long procDuration = System.currentTimeMillis() - procStart;

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Spark subprocess timed out after 40 seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("Spark subprocess exited with error code " + exitCode);
            }

            if (!Files.exists(tempOutput) || Files.size(tempOutput) == 0) {
                throw new IOException("Spark output JSON was not generated");
            }

            // Read output JSON
            AnalyticsResult sparkOutput = mapper.readValue(tempOutput.toFile(), AnalyticsResult.class);
            sparkOutput.setEngine("spark");
            sparkOutput.setDurationMs(System.currentTimeMillis() - overallStart);
            sparkOutput.setFallbackUsed(false);

            sparkSteps.add(String.format("[SPARK] Subprocess completed successfully in %d ms (exit code 0)", procDuration));
            sparkSteps.add(String.format("[SPARK] Result: %d tasks, %.1f total workload aggregated across %d clusters",
                    sparkOutput.getTotalTasks(), sparkOutput.getTotalWorkload(),
                    sparkOutput.getCountsPerCluster() != null ? sparkOutput.getCountsPerCluster().size() : 0));
            sparkOutput.setSteps(sparkSteps);

            return sparkOutput;

        } catch (Exception ex) {
            log.warn("Spark execution failed: {}. Triggering mandatory Java Streams fallback.", ex.getMessage());
            sparkSteps.add(String.format("[WARN] Spark execution failed (%s). Engaging Java Streams fallback.", ex.getMessage()));

            AnalyticsResult fallbackResult = fallbackService.computeAnalytics(
                    tasks,
                    "Java Streams Fallback (Spark process error: " + ex.getMessage() + ")",
                    System.currentTimeMillis() - overallStart,
                    true
            );

            List<String> combined = new ArrayList<>(sparkSteps);
            combined.addAll(fallbackResult.getSteps());
            fallbackResult.setSteps(combined);
            return fallbackResult;

        } finally {
            cleanupTempFile(tempInput);
            cleanupTempFile(tempOutput);
        }
    }

    /**
     * Resolves the Spark executable fat JAR by checking configured and standard locations.
     */
    private File locateSparkJar() {
        List<String> candidatePaths = List.of(
                configuredJarPath,
                "analytics-spark/target/analytics-spark-1.0.0-SNAPSHOT.jar",
                "../analytics-spark/target/analytics-spark-1.0.0-SNAPSHOT.jar",
                "analytics-spark/target/analytics-spark.jar"
        );

        for (String path : candidatePaths) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                return file;
            }
        }

        // Search in analytics-spark/target directory for any non-original jar
        File targetDir = new File("analytics-spark/target");
        if (targetDir.exists() && targetDir.isDirectory()) {
            File[] files = targetDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.startsWith("original-"));
            if (files != null && files.length > 0) {
                return files[0];
            }
        }

        return null;
    }

    /**
     * Builds the JVM command line required to run Apache Spark under Java 17.
     */
    private List<String> buildSparkCommand(String jarPath, String inputPath, String outputPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(getJavaExecutable());

        // Java 17 module add-opens required by Apache Spark internal reflection
        cmd.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.io=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.net=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.util=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/sun.nio.cs=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/sun.security.action=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/sun.util.calendar=ALL-UNNAMED");
        cmd.add("--add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED");

        cmd.add("-jar");
        cmd.add(jarPath);
        cmd.add(inputPath);
        cmd.add(outputPath);

        return cmd;
    }

    private String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            String exeName = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
            File bin = new File(javaHome, "bin" + File.separator + exeName);
            if (bin.exists()) {
                return bin.getAbsolutePath();
            }
        }
        return "java";
    }

    private void cleanupTempFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Persists the analytics run record to H2 database.
     */
    private void persistRun(AnalyticsResult result) {
        try {
            String json = mapper.writeValueAsString(result);
            AnalyticsRun run = new AnalyticsRun(
                    result.getEngine(),
                    result.getDurationMs(),
                    result.getTotalTasks(),
                    result.getTotalWorkload(),
                    "SUCCESS",
                    json
            );
            runRepository.save(run);
        } catch (Exception e) {
            log.error("Failed to persist AnalyticsRun entity: {}", e.getMessage(), e);
        }
    }

    /**
     * Retrieves tasks from repository, or seeds legacy sample tasks if repository is empty.
     */
    public List<RescueTaskEntity> getOrSeedTasks() {
        List<RescueTaskEntity> tasks = taskRepository.findAll();
        if (tasks != null && !tasks.isEmpty()) {
            return tasks;
        }

        log.info("No rescue tasks found in database; seeding default disaster relief tasks.");
        List<RescueTaskEntity> seeded = List.of(
                new RescueTaskEntity("TASK-01", "Emergency trauma medical response", 5.0, "Cluster-A", "Medical", "PENDING", null),
                new RescueTaskEntity("TASK-02", "Relief ration air-drop to Zone 1", 3.0, "Cluster-A", "Food", "PENDING", null),
                new RescueTaskEntity("TASK-03", "Field clinic antibiotic delivery", 4.0, "Cluster-B", "Medical", "PENDING", null),
                new RescueTaskEntity("TASK-04", "Structural damage survivor scan", 6.0, "Cluster-B", "Search", "PENDING", null),
                new RescueTaskEntity("TASK-05", "Burn dressing and saline transport", 2.0, "Cluster-A", "Medical", "PENDING", null),
                new RescueTaskEntity("TASK-06", "High-protein survival ration pack", 5.0, "Cluster-B", "Food", "PENDING", null),
                new RescueTaskEntity("TASK-07", "Infrared search in collapsed sector", 7.0, "Cluster-C", "Search", "PENDING", null),
                new RescueTaskEntity("TASK-08", "Water purification tablet delivery", 3.0, "Cluster-C", "Medical", "PENDING", null),
                new RescueTaskEntity("TASK-09", "Thermal imaging UAV sweep", 4.0, "Cluster-A", "Search", "PENDING", null),
                new RescueTaskEntity("TASK-10", "Dry ration emergency delivery", 2.0, "Cluster-C", "Food", "PENDING", null)
        );

        return taskRepository.saveAll(seeded);
    }

    /**
     * Retrieves the most recent analytics result from database.
     */
    public Optional<AnalyticsResult> getLatestAnalytics() {
        Optional<AnalyticsRun> latestRun = runRepository.findTopByOrderByTimestampDesc();
        if (latestRun.isEmpty()) {
            return Optional.empty();
        }

        try {
            AnalyticsResult res = mapper.readValue(latestRun.get().getResultJson(), AnalyticsResult.class);
            return Optional.of(res);
        } catch (Exception e) {
            log.error("Failed to deserialize latest analytics JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Retrieves all historical analytics runs.
     */
    public List<AnalyticsRun> getHistory() {
        return runRepository.findAllByOrderByTimestampDesc();
    }
}
