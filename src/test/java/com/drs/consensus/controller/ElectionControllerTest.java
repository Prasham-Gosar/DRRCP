package com.drs.consensus.controller;

import com.drs.consensus.election.BullyElectionService;
import com.drs.consensus.election.RingElectionService;
import com.drs.consensus.model.ElectionRecord;
import com.drs.consensus.model.ElectionResult;
import com.drs.consensus.repository.ElectionRecordRepository;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ElectionController} — Exp 4 REST endpoints.
 */
@ExtendWith(MockitoExtension.class)
class ElectionControllerTest {

    @Mock EventPublisher eventPublisher;
    @Mock ElectionRecordRepository recordRepo;

    BullyElectionService bullyService;
    RingElectionService ringService;
    ElectionController controller;

    @BeforeEach
    void setUp() {
        bullyService = new BullyElectionService(eventPublisher);
        ringService = new RingElectionService(eventPublisher);
        controller = new ElectionController(bullyService, ringService, recordRepo, eventPublisher);
    }

    private ElectionController.ElectionRequest defaultRequest() {
        return new ElectionController.ElectionRequest(
                List.of(
                        new ElectionController.NodeDto("UAV-01", 1, true),
                        new ElectionController.NodeDto("UAV-02", 2, true),
                        new ElectionController.NodeDto("Edge-01", 3, true)
                ),
                "UAV-01",
                "Cluster-A"
        );
    }

    @Test
    @DisplayName("POST /election/bully returns result with correct leader")
    void bullyEndpoint() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ElectionResult result = controller.runBully(defaultRequest());
        assertEquals("BULLY", result.getAlgorithm());
        assertEquals("Edge-01", result.getLeaderId());
        assertFalse(result.getSteps().isEmpty());
    }

    @Test
    @DisplayName("POST /election/ring returns result with correct leader")
    void ringEndpoint() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ElectionResult result = controller.runRing(defaultRequest());
        assertEquals("RING", result.getAlgorithm());
        assertEquals("Edge-01", result.getLeaderId());
        assertFalse(result.getSteps().isEmpty());
    }

    @Test
    @DisplayName("POST /election/compare returns both results, same leader")
    void compareEndpoint() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ElectionController.CompareResponse resp = controller.compareBoth(defaultRequest());
        assertTrue(resp.sameLeader());
        assertEquals("Edge-01", resp.bully().getLeaderId());
        assertEquals("Edge-01", resp.ring().getLeaderId());
        assertTrue(resp.bullyMessages() > 0);
        assertTrue(resp.ringMessages() > 0);
    }

    @Test
    @DisplayName("Election persists an ElectionRecord with correct clusterId")
    void persistsRecord() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        controller.runBully(defaultRequest());

        ArgumentCaptor<ElectionRecord> captor = ArgumentCaptor.forClass(ElectionRecord.class);
        verify(recordRepo, atLeastOnce()).save(captor.capture());
        ElectionRecord saved = captor.getValue();
        assertEquals("Cluster-A", saved.getClusterId());
        assertEquals("Edge-01", saved.getLeaderId());
    }

    @Test
    @DisplayName("currentLeader returns the elected leader after election")
    void leaderCache() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertNull(controller.currentLeader("Cluster-A"));
        controller.runBully(defaultRequest());
        assertEquals("Edge-01", controller.currentLeader("Cluster-A"));
    }

    @Test
    @DisplayName("Default nodes are used when no nodes are provided")
    void defaultNodes() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ElectionController.ElectionRequest req = new ElectionController.ElectionRequest(
                null, "UAV-01", "Cluster-A");
        ElectionResult result = controller.runBully(req);
        // Default nodes have Base-01 with priority 5 as highest
        assertEquals("Base-01", result.getLeaderId());
    }

    @Test
    @DisplayName("ELECTION_STARTED event is published before running")
    void publishesStartedEvent() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        controller.runBully(defaultRequest());
        // 1 for ELECTION_STARTED + 1 from BullyElectionService.LEADER_ELECTED
        verify(eventPublisher, atLeast(2)).publish(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Dead node is skipped — next-highest alive node wins")
    void deadNodeSkipped() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ElectionController.ElectionRequest req = new ElectionController.ElectionRequest(
                List.of(
                        new ElectionController.NodeDto("UAV-01", 1, true),
                        new ElectionController.NodeDto("UAV-02", 2, true),
                        new ElectionController.NodeDto("Edge-01", 3, false)
                ),
                "UAV-01",
                "Cluster-A"
        );
        ElectionResult result = controller.runBully(req);
        assertEquals("UAV-02", result.getLeaderId());
    }
}
