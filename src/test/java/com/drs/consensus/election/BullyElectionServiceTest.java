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
 * Tests for {@link BullyElectionService}.
 * Uses the same scenario from the README: 3 nodes, UAV-01 initiates.
 */
@ExtendWith(MockitoExtension.class)
class BullyElectionServiceTest {

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    BullyElectionService service;

    /** The README scenario: UAV-01(p=1), UAV-02(p=2), Edge-01(p=3), all alive. */
    private List<ElectionNode> readmeNodes() {
        return List.of(
                new ElectionNode("UAV-01", 1, true),
                new ElectionNode("UAV-02", 2, true),
                new ElectionNode("Edge-01", 3, true)
        );
    }

    @Test
    @DisplayName("Bully elects the highest-priority alive node (Edge-01, p=3)")
    void electsHighestPriority() {
        ElectionResult result = service.runElection(readmeNodes(), "UAV-01");
        assertEquals("Edge-01", result.getLeaderId());
        assertEquals(3, result.getLeaderPriority());
        assertEquals("BULLY", result.getAlgorithm());
    }

    @Test
    @DisplayName("Bully and Ring elect the same leader on the README example")
    void bullyAndRingElectSameLeader() {
        // We run both Bully and Ring on the same node list and assert same leader
        RingElectionService ringService = new RingElectionService(eventPublisher);
        List<ElectionNode> nodes = readmeNodes();

        ElectionResult bullyResult = service.runElection(nodes, "UAV-01");
        ElectionResult ringResult = ringService.runElection(nodes, "UAV-01");

        assertEquals(bullyResult.getLeaderId(), ringResult.getLeaderId(),
                "Bully and Ring must elect the same leader");
        assertEquals(bullyResult.getLeaderPriority(), ringResult.getLeaderPriority());
    }

    @Test
    @DisplayName("Bully step trace is non-empty and contains key events")
    void stepsArePopulated() {
        ElectionResult result = service.runElection(readmeNodes(), "UAV-01");
        assertFalse(result.getSteps().isEmpty());
        assertTrue(result.getSteps().get(0).contains("starts an election"));
        assertTrue(result.getSteps().get(result.getSteps().size() - 1).contains("Election complete"));
    }

    @Test
    @DisplayName("Bully message count is positive")
    void messageCountPositive() {
        ElectionResult result = service.runElection(readmeNodes(), "UAV-01");
        assertTrue(result.getTotalMessages() > 0);
    }

    @Test
    @DisplayName("When highest-priority node is dead, next-highest wins")
    void deadNodeSkipped() {
        List<ElectionNode> nodes = List.of(
                new ElectionNode("UAV-01", 1, true),
                new ElectionNode("UAV-02", 2, true),
                new ElectionNode("Edge-01", 3, false) // dead
        );
        ElectionResult result = service.runElection(nodes, "UAV-01");
        assertEquals("UAV-02", result.getLeaderId());
    }

    @Test
    @DisplayName("Single alive node elects itself")
    void singleNodeElectsSelf() {
        List<ElectionNode> nodes = List.of(
                new ElectionNode("UAV-01", 1, true),
                new ElectionNode("UAV-02", 2, false),
                new ElectionNode("Edge-01", 3, false)
        );
        ElectionResult result = service.runElection(nodes, "UAV-01");
        assertEquals("UAV-01", result.getLeaderId());
    }

    @Test
    @DisplayName("Unknown initiator throws NoSuchElementException")
    void unknownInitiator() {
        assertThrows(NoSuchElementException.class,
                () -> service.runElection(readmeNodes(), "GHOST-99"));
    }
}
