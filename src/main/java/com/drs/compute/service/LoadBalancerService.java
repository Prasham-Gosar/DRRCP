package com.drs.compute.service;

import com.drs.compute.balancer.LoadBalancer;
import com.drs.compute.model.AssignmentResult;
import com.drs.compute.model.LoadNode;
import com.drs.compute.model.RescueTask;
import com.drs.compute.model.RescueTaskEntity;
import com.drs.compute.repository.RescueTaskRepository;
import com.drs.core.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service orchestrating load balancing decisions across nodes.
 * Replaces console output with structured {@link AssignmentResult} objects
 * and publishes live SSE events via {@link EventPublisher}.
 */
@Service
public class LoadBalancerService {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerService.class);

    private final EventPublisher eventPublisher;
    private final RescueTaskRepository taskRepository;

    public LoadBalancerService(EventPublisher eventPublisher) {
        this(eventPublisher, null);
    }

    @Autowired
    public LoadBalancerService(EventPublisher eventPublisher,
                               @Autowired(required = false) RescueTaskRepository taskRepository) {
        this.eventPublisher = eventPublisher;
        this.taskRepository = taskRepository;
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
            taskRepository.save(entity);
            log.info("Task {} assigned to node {}", taskId, result.getChosenNodeId());
        }

        return result;
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
}
