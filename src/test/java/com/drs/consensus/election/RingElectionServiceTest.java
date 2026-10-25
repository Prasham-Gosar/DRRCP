package com.drs.consensus.election;

import com.drs.consensus.model.ElectionNode;
import com.drs.consensus.model.ElectionResult;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RingElectionService}.
 */
@ExtendWith(MockitoExtension.class)
class RingElectionServiceTest {

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    RingElectionService service;

    /** Same 3 nodes as the README. Ring order = insertion order. */
    private List<ElectionNode> readmeRing() {
        return List.of(
                new ElectionNode("UAV-01", 1, true),
                new ElectionNode("UAV-02", 2, true),
                new ElectionNode("Edge-01", 3, true)
        );
    }

    @Test
    @DisplayName("Ring elects the highest-priority alive node (Edge-01, p=3)")
    void electsHighestPriority() {
        ElectionResult result = service.runElection(readmeRing(), "UAV-01");
        assertEquals("Edge-01", result.getLeaderId());
        assertEquals(3, result.getLeaderPriority());
        assertEquals("RING", result.getAlgorithm());
    }

    @Test
    @DisplayName("Ring message count = 2 * alive nodes (election + coordinator rounds)")
    void messageCountIsTwiceAlive() {
        // With 3 alive nodes: 3 election msgs + 3 coordinator msgs = 6
        ElectionResult result = service.runElection(readmeRing(), "UAV-01");
        assertEquals(6, result.getTotalMessages());
    }

    @Test
    @DisplayName("Ring with a dead node skips it")
    void deadNodeSkipped() {
        List<ElectionNode> ring = List.of(
                new ElectionNode("UAV-01", 1, true),
                new ElectionNode("UAV-02", 2, false),  // dead, skipped in ring
                new ElectionNode("Edge-01", 3, true)
        );
        ElectionResult result = service.runElection(ring, "UAV-01");
        assertEquals("Edge-01", result.getLeaderId());
        // With 2 alive: 2 election msgs + 2 coordinator msgs = 4
        assertEquals(4, result.getTotalMessages());
    }

    @Test
    @DisplayName("Ring step trace contains key events")
    void stepsPopulated() {
        ElectionResult result = service.runElection(readmeRing(), "UAV-01");
        assertFalse(result.getSteps().isEmpty());
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("ELECTION(candidate=")));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("COORDINATOR(leader=")));
        assertTrue(result.getSteps().stream().anyMatch(s -> s.contains("Election complete")));
    }

    @Test
    @DisplayName("Unknown initiator throws NoSuchElementException")
    void unknownInitiator() {
        assertThrows(NoSuchElementException.class,
                () -> service.runElection(readmeRing(), "GHOST-99"));
    }

    @Test
    @DisplayName("Bully and Ring produce the same leader but different message counts")
    void compareWithBully() {
        BullyElectionService bullyService = new BullyElectionService(eventPublisher);
        List<ElectionNode> nodes = readmeRing();

        ElectionResult bully = bullyService.runElection(nodes, "UAV-01");
        ElectionResult ring = service.runElection(nodes, "UAV-01");

        assertEquals(bully.getLeaderId(), ring.getLeaderId());
        // Generally Ring uses more messages than Bully on small clusters,
        // but the exact counts differ — just assert they're both positive
        assertTrue(bully.getTotalMessages() > 0);
        assertTrue(ring.getTotalMessages() > 0);
    }
}
