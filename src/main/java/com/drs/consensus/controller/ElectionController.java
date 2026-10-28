package com.drs.consensus.controller;

import com.drs.consensus.LeaderProvider;
import com.drs.consensus.election.BullyElectionService;
import com.drs.consensus.election.RingElectionService;
import com.drs.consensus.model.ElectionNode;
import com.drs.consensus.model.ElectionRecord;
import com.drs.consensus.model.ElectionResult;
import com.drs.consensus.repository.ElectionRecordRepository;
import com.drs.core.event.EventPublisher;
import com.drs.core.model.NodeEntity;
import com.drs.core.model.NodeEntityRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin REST controller for Exp 4: Leader Election (Bully &amp; Ring).
 * <p>
 * Implements {@link LeaderProvider} so other modules (e.g. Parth's LoadBalancer)
 * can inject it to obtain the current leader.
 * All algorithm logic lives in {@link BullyElectionService} and {@link RingElectionService}.
 * This controller: validates input, delegates, persists {@link ElectionRecord} history,
 * publishes SSE events, and exposes leader queries.
 */
@RestController
@RequestMapping("/api/election")
public class ElectionController implements LeaderProvider {

    private final BullyElectionService bullyService;
    private final RingElectionService ringService;
    private final ElectionRecordRepository recordRepo;
    private final EventPublisher eventPublisher;

    @Autowired(required = false)
    private NodeEntityRepository nodeEntityRepo;

    /** In-memory leader cache: clusterId → leaderId. Used by other modules. */
    private final Map<String, String> leaderCache = new ConcurrentHashMap<>();

    /** Default nodes used when the request body omits the list and repository has none. */
    private static final List<ElectionNode> DEFAULT_NODES = List.of(
            new ElectionNode("UAV-01", 1, true),
            new ElectionNode("UAV-02", 2, true),
            new ElectionNode("Edge-01", 3, true),
            new ElectionNode("Edge-02", 4, true),
            new ElectionNode("Base-01", 5, true)
    );

    @Autowired
    public ElectionController(BullyElectionService bullyService,
                              RingElectionService ringService,
                              ElectionRecordRepository recordRepo,
                              EventPublisher eventPublisher) {
        this.bullyService = bullyService;
        this.ringService = ringService;
        this.recordRepo = recordRepo;
        this.eventPublisher = eventPublisher;
    }

    // ── Request / Response DTOs ──

    public record NodeDto(String id, int priority, boolean alive) { }

    public record ElectionRequest(List<NodeDto> nodes,
                                  @NotBlank String initiatorId,
                                  String clusterId) { }

    public record CompareResponse(ElectionResult bully, ElectionResult ring,
                                  boolean sameLeader, int bullyMessages, int ringMessages) { }

    // ── POST /api/election/bully ──

    @PostMapping("/bully")
    public ElectionResult runBully(@Valid @RequestBody ElectionRequest req) {
        String cluster = resolveCluster(req);
        List<ElectionNode> nodes = toElectionNodes(req.nodes(), cluster);
        String initiatorId = req.initiatorId();

        eventPublisher.publish("consensus", "ELECTION_STARTED",
                Map.of("algorithm", "BULLY", "initiatorId", initiatorId, "nodeCount", nodes.size(), "clusterId", cluster));

        ElectionResult result = bullyService.runElection(nodes, initiatorId);
        persistRecord(result, cluster);
        return result;
    }

    // ── POST /api/election/ring ──

    @PostMapping("/ring")
    public ElectionResult runRing(@Valid @RequestBody ElectionRequest req) {
        String cluster = resolveCluster(req);
        List<ElectionNode> nodes = toElectionNodes(req.nodes(), cluster);
        String initiatorId = req.initiatorId();

        eventPublisher.publish("consensus", "ELECTION_STARTED",
                Map.of("algorithm", "RING", "initiatorId", initiatorId, "nodeCount", nodes.size(), "clusterId", cluster));

        ElectionResult result = ringService.runElection(nodes, initiatorId);
        persistRecord(result, cluster);
        return result;
    }

    // ── POST /api/election/compare ──

    @PostMapping("/compare")
    public CompareResponse compareBoth(@Valid @RequestBody ElectionRequest req) {
        String cluster = resolveCluster(req);
        List<ElectionNode> nodes = toElectionNodes(req.nodes(), cluster);
        String initiatorId = req.initiatorId();

        eventPublisher.publish("consensus", "ELECTION_STARTED",
                Map.of("algorithm", "COMPARE", "initiatorId", initiatorId, "nodeCount", nodes.size(), "clusterId", cluster));

        // Need separate copies because election services may mutate iteration state
        List<ElectionNode> nodesForBully = copyNodes(nodes);
        List<ElectionNode> nodesForRing = copyNodes(nodes);

        ElectionResult bully = bullyService.runElection(nodesForBully, initiatorId);
        ElectionResult ring = ringService.runElection(nodesForRing, initiatorId);

        persistRecord(bully, cluster);
        persistRecord(ring, cluster);

        return new CompareResponse(
                bully, ring,
                bully.getLeaderId().equals(ring.getLeaderId()),
                bully.getTotalMessages(), ring.getTotalMessages());
    }

    // ── GET /api/election/history ──

    @GetMapping("/history")
    public List<ElectionRecord> history(@RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return recordRepo.findTopNByOrderByTimestampDesc(safeLimit);
    }

    // ── GET /api/election/leader?clusterId=Cluster-A ──

    /**
     * Returns the most recently elected leader for the given cluster.
     * Other modules (e.g. Parth's LoadBalancer) can call this.
     */
    @GetMapping("/leader")
    public ResponseEntity<Map<String, String>> leader(
            @RequestParam(defaultValue = "Cluster-A") String clusterId) {
        String leader = currentLeader(clusterId);
        if (leader == null) {
            return ResponseEntity.ok(Map.of("clusterId", clusterId, "leader", "NONE"));
        }
        return ResponseEntity.ok(Map.of("clusterId", clusterId, "leader", leader));
    }

    /**
     * Programmatic API from {@link LeaderProvider} — called internally by other Spring services.
     *
     * @param clusterId the cluster to query
     * @return the current leader's ID, or null if no election has run
     */
    @Override
    public String currentLeader(String clusterId) {
        return leaderCache.get(clusterId);
    }

    // ── helpers ──

    private List<ElectionNode> toElectionNodes(List<NodeDto> dtos, String clusterId) {
        if (dtos != null && !dtos.isEmpty()) {
            List<ElectionNode> nodes = new ArrayList<>();
            for (NodeDto dto : dtos) {
                nodes.add(new ElectionNode(dto.id(), dto.priority(), dto.alive()));
            }
            return nodes;
        }

        // Fall back to Cluster nodes from repository if available
        if (nodeEntityRepo != null) {
            try {
                List<NodeEntity> entities = nodeEntityRepo.findByClusterId(clusterId);
                if (entities != null && !entities.isEmpty()) {
                    List<ElectionNode> nodes = new ArrayList<>();
                    for (NodeEntity e : entities) {
                        int priority = e.getConnectivityScore() > 0 ? e.getConnectivityScore() : 1;
                        boolean alive = !"SILENT".equalsIgnoreCase(e.getStatus()) &&
                                        !"PARTITIONED".equalsIgnoreCase(e.getState());
                        nodes.add(new ElectionNode(e.getNodeId(), priority, alive));
                    }
                    return nodes;
                }
            } catch (Exception ignored) {
                // Ignore and fall back to hardcoded defaults
            }
        }

        return new ArrayList<>(DEFAULT_NODES);
    }

    private List<ElectionNode> copyNodes(List<ElectionNode> src) {
        List<ElectionNode> copy = new ArrayList<>();
        for (ElectionNode n : src) {
            copy.add(new ElectionNode(n.getId(), n.getPriority(), n.isAlive()));
        }
        return copy;
    }

    private String resolveCluster(ElectionRequest req) {
        return (req.clusterId() != null && !req.clusterId().isBlank())
                ? req.clusterId() : "Cluster-A";
    }

    private void persistRecord(ElectionResult result, String clusterId) {
        ElectionRecord record = new ElectionRecord(
                result.getAlgorithm(),
                result.getLeaderId(),
                result.getLeaderPriority(),
                result.getTotalMessages(),
                clusterId);
        recordRepo.save(record);
        leaderCache.put(clusterId, result.getLeaderId());
    }
}
