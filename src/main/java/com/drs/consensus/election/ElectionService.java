package com.drs.consensus.election;

import com.drs.consensus.LeaderProvider;
import com.drs.consensus.controller.ElectionController.CompareResponse;
import com.drs.consensus.model.ElectionNode;
import com.drs.consensus.model.ElectionRecord;
import com.drs.consensus.model.ElectionResult;
import com.drs.consensus.repository.ElectionRecordRepository;
import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified Election Service for Scenario Runner integration.
 */
@Service
public class ElectionService implements LeaderProvider {

    private final BullyElectionService bullyService;
    private final RingElectionService ringService;
    private final ElectionRecordRepository recordRepo;
    private final EventPublisher eventPublisher;

    @Autowired(required = false)
    private NodeEntityRepository nodeEntityRepo;

    private final ConcurrentHashMap<String, String> currentLeaders = new ConcurrentHashMap<>();

    private static final List<ElectionNode> DEFAULT_NODES = List.of(
            new ElectionNode("UAV-01", 1, true),
            new ElectionNode("UAV-02", 2, true),
            new ElectionNode("Edge-01", 3, true),
            new ElectionNode("Edge-02", 4, true),
            new ElectionNode("Base-01", 5, true)
    );

    public ElectionService(BullyElectionService bullyService,
                           RingElectionService ringService,
                           ElectionRecordRepository recordRepo,
                           EventPublisher eventPublisher) {
        this.bullyService = bullyService;
        this.ringService = ringService;
        this.recordRepo = recordRepo;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Run both Bully and Ring elections for a cluster and return comparison.
     * Exposed for Prasham's ScenarioRunner.
     */
    public CompareResponse runBothElections(String clusterId) {
        String cluster = (clusterId != null && !clusterId.isBlank()) ? clusterId : "Cluster-A";
        List<ElectionNode> nodes = resolveNodesForCluster(cluster);
        String initiatorId = nodes.stream()
                .filter(ElectionNode::isAlive)
                .findFirst()
                .map(ElectionNode::getId)
                .orElse("UAV-01");

        List<ElectionNode> forBully = copyNodes(nodes);
        List<ElectionNode> forRing = copyNodes(nodes);

        ElectionResult bully = bullyService.runElection(forBully, initiatorId);
        ElectionResult ring = ringService.runElection(forRing, initiatorId);

        currentLeaders.put(cluster, bully.getLeaderId());

        persist(bully, cluster);
        persist(ring, cluster);

        eventPublisher.publish("consensus", "ELECTION_COMPLETED", Map.of(
                "clusterId", cluster,
                "bullyLeader", bully.getLeaderId(),
                "ringLeader", ring.getLeaderId()
        ));

        return new CompareResponse(
                bully, ring,
                bully.getLeaderId().equals(ring.getLeaderId()),
                bully.getTotalMessages(), ring.getTotalMessages()
        );
    }

    @Override
    public String currentLeader(String clusterId) {
        String cluster = (clusterId != null && !clusterId.isBlank()) ? clusterId : "Cluster-A";
        return currentLeaders.getOrDefault(cluster, "Base-01");
    }

    private List<ElectionNode> resolveNodesForCluster(String clusterId) {
        if (nodeEntityRepo != null) {
            try {
                List<NodeEntity> entities = nodeEntityRepo.findByClusterId(clusterId);
                if (entities != null && !entities.isEmpty()) {
                    List<ElectionNode> list = new ArrayList<>();
                    int p = 1;
                    for (NodeEntity e : entities) {
                        list.add(new ElectionNode(e.getNodeId(), p++, !"DEAD".equalsIgnoreCase(e.getStatus())));
                    }
                    return list;
                }
            } catch (Exception ignored) {}
        }
        return copyNodes(DEFAULT_NODES);
    }

    private List<ElectionNode> copyNodes(List<ElectionNode> nodes) {
        List<ElectionNode> copies = new ArrayList<>(nodes.size());
        for (ElectionNode n : nodes) {
            copies.add(new ElectionNode(n.getId(), n.getPriority(), n.isAlive()));
        }
        return copies;
    }

    private void persist(ElectionResult res, String cluster) {
        if (recordRepo == null) return;
        ElectionRecord rec = new ElectionRecord(
                res.getAlgorithm(), res.getLeaderId(), res.getLeaderPriority(),
                res.getTotalMessages(), cluster);
        recordRepo.save(rec);
    }
}
