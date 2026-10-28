package com.drs.compute.service;

import com.drs.compute.balancer.LoadBalancer;
import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.RescueTask;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.repository.RescueTaskRepository;
import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service orchestrating load balancing decisions across nodes (Exp 6).
 * Manages active candidate nodes (from Prasham's NodeEntityRepository or default stubs),
 * tracks node loads and availability for dynamic re-balancing demos,
 * assigns tasks using lowest projected utilization, and publishes live SSE events.
 */
@Service
public class LoadBalancerService {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerService.class);

    private final EventPublisher eventPublisher;
    private final RescueTaskRepository taskRepository;
    private final NodeEntityRepository nodeEntityRepo;

    /** Active candidate nodes maintained in memory for live load balancing */
    private final Map<String, LoadNode> activeNodes = new ConcurrentHashMap<>();

    private final AtomicInteger taskSequence = new AtomicInteger(1);

    /** Default stub nodes used when NodeEntityRepository is empty or unpopulated */
    public static final List<LoadNode> DEFAULT_STUB_NODES = List.of(
            new LoadNode("UAV-01", 80, 70, 10.0),    // effCap = 7.50
            new LoadNode("UAV-02", 60, 55, 10.0),    // effCap = 5.75
            new LoadNode("Edge-01", 90, 85, 15.0),   // effCap = 13.125
            new LoadNode("Edge-02", 85, 75, 15.0)    // effCap = 12.00
    );

    public LoadBalancerService(EventPublisher eventPublisher) {
        this(eventPublisher, null, null);
    }

    public LoadBalancerService(EventPublisher eventPublisher,
                               RescueTaskRepository taskRepository) {
        this(eventPublisher, taskRepository, null);
    }

    @Autowired
    public LoadBalancerService(EventPublisher eventPublisher,
                               @Autowired(required = false) RescueTaskRepository taskRepository,
                               @Autowired(required = false) NodeEntityRepository nodeEntityRepo) {
        this.eventPublisher = eventPublisher;
        this.taskRepository = taskRepository;
        this.nodeEntityRepo = nodeEntityRepo;
        initNodes();
    }

    /**
     * Initializes candidate nodes from Prasham's NodeEntityRepository if present,
     * or falls back to default stub nodes.
     */
    @PostConstruct
    public synchronized void initNodes() {
        activeNodes.clear();

        // 1. Try to load from Prasham's NodeEntityRepository
        if (nodeEntityRepo != null) {
            try {
                List<NodeEntity> entities = nodeEntityRepo.findAll();
                if (entities != null && !entities.isEmpty()) {
                    for (NodeEntity e : entities) {
                        double maxCap = "EDGE".equalsIgnoreCase(e.getNodeType()) ? 15.0 :
                                        ("BASE".equalsIgnoreCase(e.getNodeType()) || "SERVER".equalsIgnoreCase(e.getNodeType())) ? 25.0 : 10.0;
                        LoadNode node = new LoadNode(e.getNodeId(), e.getBattery(), e.getSignal(), maxCap);
                        node.setClusterId(e.getClusterId());
                        node.setNodeType(e.getNodeType());
                        boolean avail = !"SILENT".equalsIgnoreCase(e.getStatus()) &&
                                        !"PARTITIONED".equalsIgnoreCase(e.getState());
                        node.setAvailable(avail);
                        activeNodes.put(node.getNodeId(), node);
                    }
                    log.info("Initialized {} LoadNodes from NodeEntityRepository", activeNodes.size());
                    return;
                }
            } catch (Exception ex) {
                log.warn("Failed to load from NodeEntityRepository, falling back to stub nodes: {}", ex.getMessage());
            }
        }

        // 2. Fall back to default stub nodes
        for (LoadNode stub : DEFAULT_STUB_NODES) {
            LoadNode copy = new LoadNode(stub.getNodeId(), stub.getBatteryLevel(), stub.getSignalStrength(), stub.getMaxCapacity());
            copy.setClusterId(stub.getClusterId());
            copy.setNodeType(stub.getNodeType());
            copy.setAvailable(stub.isAvailable());
            copy.setCurrentLoad(0.0);
            activeNodes.put(copy.getNodeId(), copy);
        }
        log.info("Initialized {} LoadNodes with default stubs", activeNodes.size());
    }

    /**
     * Returns candidate nodes currently tracked in the load balancer.
     */
    public List<LoadNode> getCandidateNodes() {
        if (activeNodes.isEmpty()) {
            initNodes();
        }
        return new ArrayList<>(activeNodes.values());
    }

    /**
     * Updates candidate node availability for demoing node failure and re-balancing.
     */
    public LoadNode setNodeAvailability(String nodeId, boolean available) {
        LoadNode node = activeNodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        node.setAvailable(available);
        log.info("Node {} availability updated to {}", nodeId, available);
        return node;
    }

    /**
     * Resets candidate node loads back to 0.0 and restores availability.
     */
    public List<LoadNode> resetNodes() {
        initNodes();
        return getCandidateNodes();
    }

    /**
     * Assigns a single task across the given candidate nodes.
     *
     * @param nodes candidate nodes
     * @param task  task to assign
     * @return assignment result with chosen node and per-node traces
     */
    public AssignmentResult assignTask(List<LoadNode> nodes, RescueTask task) {
        LoadBalancer balancer = new LoadBalancer(nodes);
        AssignmentResult result = balancer.assignTask(task);

        if (eventPublisher != null) {
            eventPublisher.publish("compute", "TASK_ASSIGNED", result);
        }

        return result;
    }

    /**
     * Assigns a persisted task entity by its taskId, updating its status to ASSIGNED.
     *
     * @param nodes  candidate nodes
     * @param taskId ID of the task in database
     * @return assignment result
     */
    public AssignmentResult assignTaskEntity(List<LoadNode> nodes, String taskId) {
        if (taskRepository == null) {
            throw new IllegalStateException("RescueTaskRepository is not configured");
        }

        RescueTaskEntity entity = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("RescueTask not found: " + taskId));

        RescueTask task = RescueTask.fromEntity(entity);
        AssignmentResult result = assignTask(nodes, task);

        if (result.isSuccess()) {
            entity.setStatus("ASSIGNED");
            entity.setAssignedNodeId(result.getChosenNodeId());
            entity.setAssignedAt(Instant.now());
            taskRepository.save(entity);
            log.info("Task {} assigned to node {}", taskId, result.getChosenNodeId());
        }

        return result;
    }

    /**
     * Assigns a persisted task entity using active candidate nodes.
     *
     * @param taskId ID of the task in database
     * @return assignment result
     */
    public AssignmentResult assignTaskEntity(String taskId) {
        return assignTaskEntity(getCandidateNodes(), taskId);
    }

    /**
     * Sequentially assigns a batch of rescue tasks across the candidate nodes.
     *
     * @param nodes candidate nodes
     * @param tasks list of rescue tasks
     * @return list of assignment results
     */
    public List<AssignmentResult> assignAll(List<LoadNode> nodes, List<RescueTask> tasks) {
        List<AssignmentResult> results = new ArrayList<>();
        LoadBalancer balancer = new LoadBalancer(nodes);

        for (RescueTask task : tasks) {
            AssignmentResult res = balancer.assignTask(task);
            results.add(res);
            if (eventPublisher != null) {
                eventPublisher.publish("compute", "TASK_ASSIGNED", res);
            }
        }

        return results;
    }

    /**
     * Assigns all pending rescue tasks from the repository across the active candidate nodes.
     * Updates each task entity to ASSIGNED and updates candidate node loads.
     *
     * @return list of assignment results
     */
    public List<AssignmentResult> assignAllPending() {
        if (taskRepository == null) {
            throw new IllegalStateException("RescueTaskRepository is not configured");
        }
        List<RescueTaskEntity> pending = taskRepository.findByStatus("PENDING");
        if (pending == null || pending.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort by taskId for deterministic sequential assignment
        pending.sort(Comparator.comparing(RescueTaskEntity::getTaskId));

        List<AssignmentResult> results = new ArrayList<>();
        List<LoadNode> nodes = getCandidateNodes();

        for (RescueTaskEntity entity : pending) {
            RescueTask task = RescueTask.fromEntity(entity);
            AssignmentResult result = assignTask(nodes, task);
            if (result.isSuccess()) {
                entity.setStatus("ASSIGNED");
                entity.setAssignedNodeId(result.getChosenNodeId());
                entity.setAssignedAt(Instant.now());
                taskRepository.save(entity);
                log.info("Pending task {} assigned to node {}", entity.getTaskId(), result.getChosenNodeId());
            }
            results.add(result);
        }

        return results;
    }

    /**
     * Creates and persists a new rescue task, publishing a TASK_CREATED event.
     */
    public RescueTaskEntity createTask(RescueTaskEntity task) {
        if (taskRepository == null) {
            throw new IllegalStateException("RescueTaskRepository is not configured");
        }
        if (task.getTaskId() == null || task.getTaskId().isBlank()) {
            task.setTaskId(generateTaskId());
        }
        if (task.getDescription() == null || task.getDescription().isBlank()) {
            task.setDescription("Rescue Mission " + task.getTaskId());
        }
        if (task.getWorkload() <= 0) {
            task.setWorkload(1.0);
        }
        if (task.getClusterId() == null || task.getClusterId().isBlank()) {
            task.setClusterId("Cluster-A");
        }
        if (task.getType() == null || task.getType().isBlank()) {
            task.setType("General");
        }
        task.setStatus("PENDING");
        task.setAssignedNodeId(null);
        task.setCreatedAt(Instant.now());

        RescueTaskEntity saved = taskRepository.save(task);

        if (eventPublisher != null) {
            eventPublisher.publish("compute", "TASK_CREATED", saved);
        }

        log.info("Created rescue task: {} (workload: {})", saved.getTaskId(), saved.getWorkload());
        return saved;
    }

    private String generateTaskId() {
        long count = taskRepository != null ? taskRepository.count() : 0;
        int next = (int) count + taskSequence.getAndIncrement();
        return String.format("TASK-%02d", next);
    }

    /**
     * Seeds legacy sample tasks (Medical, Food, Search...) in PENDING status.
     */
    public List<RescueTaskEntity> seedSampleTasks() {
        if (taskRepository == null) {
            throw new IllegalStateException("RescueTaskRepository is not configured");
        }

        List<RescueTaskEntity> samples = List.of(
                new RescueTaskEntity("TASK-01", "Search for survivors in Sector 1", 3.0, "Cluster-A", "Search", "PENDING", null),
                new RescueTaskEntity("TASK-02", "Inspect damaged school building", 2.0, "Cluster-A", "Search", "PENDING", null),
                new RescueTaskEntity("TASK-03", "Deliver emergency medical supplies", 4.0, "Cluster-B", "Medical", "PENDING", null),
                new RescueTaskEntity("TASK-04", "Air-drop ration packets to relief camp", 2.0, "Cluster-A", "Food", "PENDING", null),
                new RescueTaskEntity("TASK-05", "Relay emergency radio comms", 3.0, "Cluster-B", "Search", "PENDING", null),
                new RescueTaskEntity("TASK-06", "Thermal scan of collapsed warehouse", 2.0, "Cluster-C", "Search", "PENDING", null),
                new RescueTaskEntity("TASK-07", "Transport trauma triage kit", 5.0, "Cluster-A", "Medical", "PENDING", null),
                new RescueTaskEntity("TASK-08", "Water purification supply delivery", 3.0, "Cluster-B", "Food", "PENDING", null)
        );

        List<RescueTaskEntity> created = new ArrayList<>();
        for (RescueTaskEntity sample : samples) {
            sample.setCreatedAt(Instant.now());
            sample.setStatus("PENDING");
            sample.setAssignedNodeId(null);
            sample.setAssignedAt(null);
            RescueTaskEntity saved = taskRepository.save(sample);
            created.add(saved);
            if (eventPublisher != null) {
                eventPublisher.publish("compute", "TASK_CREATED", saved);
            }
        }

        log.info("Seeded {} legacy sample rescue tasks", created.size());
        return created;
    }

    /**
     * Returns all rescue tasks, optionally filtered by status and clusterId.
     */
    public List<RescueTaskEntity> getAllTasks(String status, String clusterId) {
        if (taskRepository == null) {
            return Collections.emptyList();
        }
        List<RescueTaskEntity> list;
        if (status != null && !status.isBlank()) {
            list = taskRepository.findByStatus(status.toUpperCase());
        } else {
            list = taskRepository.findAll();
        }

        if (clusterId != null && !clusterId.isBlank()) {
            list = list.stream()
                    .filter(t -> clusterId.equalsIgnoreCase(t.getClusterId()))
                    .collect(Collectors.toList());
        }

        // Sort: PENDING first, then by taskId
        list.sort(Comparator.comparing((RescueTaskEntity t) -> "PENDING".equalsIgnoreCase(t.getStatus()) ? 0 : 1)
                .thenComparing(RescueTaskEntity::getTaskId));
        return list;
    }

    /**
     * Deletes all tasks from repository.
     */
    public void deleteAllTasks() {
        if (taskRepository != null) {
            taskRepository.deleteAll();
            log.info("Cleared all rescue tasks from database");
        }
    }
}
