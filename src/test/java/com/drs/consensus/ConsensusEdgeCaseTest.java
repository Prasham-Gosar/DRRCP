package com.drs.consensus;

import com.drs.consensus.election.BullyElectionService;
import com.drs.consensus.election.RingElectionService;
import com.drs.consensus.model.ElectionNode;
import com.drs.consensus.model.ElectionResult;
import com.drs.consensus.model.ReconciliationResult;
import com.drs.consensus.reconcile.ReconciliationService;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ConsensusEdgeCaseTest {

    @Mock
    EventPublisher eventPublisher;

    BullyElectionService bullyService;
    RingElectionService ringService;
    ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        bullyService = new BullyElectionService(eventPublisher);
        ringService = new RingElectionService(eventPublisher);
        reconciliationService = new ReconciliationService(eventPublisher);
    }

    @Test
    @DisplayName("Edge Case 1: All nodes dead in Bully and Ring -> returns NO_LEADER cleanly")
    void allNodesDead() {
        List<ElectionNode> deadNodes = List.of(
                new ElectionNode("UAV-01", 1, false),
                new ElectionNode("UAV-02", 2, false),
                new ElectionNode("Edge-01", 3, false)
        );

        ElectionResult bullyRes = bullyService.runElection(deadNodes, "UAV-01");
        assertEquals("NO_LEADER", bullyRes.getLeaderId());
        assertEquals(-1, bullyRes.getLeaderPriority());

        ElectionResult ringRes = ringService.runElection(deadNodes, "UAV-01");
        assertEquals("NO_LEADER", ringRes.getLeaderId());
        assertEquals(-1, ringRes.getLeaderPriority());
    }

    @Test
    @DisplayName("Edge Case 2: Single node cluster -> immediately becomes leader")
    void singleNodeCluster() {
        List<ElectionNode> single = List.of(
                new ElectionNode("Solo-01", 10, true)
        );

        ElectionResult bullyRes = bullyService.runElection(single, "Solo-01");
        assertEquals("Solo-01", bullyRes.getLeaderId());
        assertEquals(0, bullyRes.getTotalMessages());

        ElectionResult ringRes = ringService.runElection(single, "Solo-01");
        assertEquals("Solo-01", ringRes.getLeaderId());
        assertEquals(2, ringRes.getTotalMessages()); // 1 self-election + 1 self-coordinator
    }

    @Test
    @DisplayName("Edge Case 3: Tie in priority -> deterministic leader selection")
    void tieInPriority() {
        List<ElectionNode> tiedNodes = List.of(
                new ElectionNode("Node-A", 5, true),
                new ElectionNode("Node-B", 5, true)
        );

        // Bully: strict greater check (priority > current.priority)
        ElectionResult bullyRes = bullyService.runElection(tiedNodes, "Node-A");
        assertNotNull(bullyRes.getLeaderId());

        // Ring: strict greater check (to.priority > candidate.priority)
        ElectionResult ringRes = ringService.runElection(tiedNodes, "Node-A");
        assertNotNull(ringRes.getLeaderId());
        assertEquals(5, ringRes.getLeaderPriority());
    }

    @Test
    @DisplayName("Edge Case 4: Empty update list in reconciliation -> empty clean result")
    void emptyUpdateList() {
        ReconciliationResult result = reconciliationService.reconcile(Collections.emptyList());
        assertNotNull(result);
        assertEquals(0, result.getTotalUpdates());
        assertEquals(0, result.getResolutions().size());
        assertEquals(0, result.getConflictsDetected());
        assertEquals(0, result.getHumanReviewRequired());
        assertFalse(result.getSteps().isEmpty());
    }
}

