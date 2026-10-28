package com.drs.comm;

import com.drs.comm.model.CollectiveResult;
import com.drs.comm.model.CommRequest;
import com.drs.comm.model.RankExecutionRecord;
import com.drs.comm.model.RankTimelineEntry;
import com.drs.comm.service.CommService;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommServiceTest {

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private CommService service;

    @Test
    @DisplayName("runAll: executes full disaster relief collective communication workflow")
    void testRunAll() {
        CommRequest req = new CommRequest(4, 2, 0);
        req.setAlertText("URGENT: Flash Flood Inundation - Evacuate Zone A to D");

        CollectiveResult result = service.runAll(req);

        assertNotNull(result);
        assertEquals("RUN_ALL", result.getOperation());
        assertEquals(4, result.getProcesses());
        assertEquals(2, result.getChunkSize());
        assertEquals(0, result.getRootRank());
        assertTrue(result.isSelfCheckPassed(), "Self-check verification must pass");
        assertTrue(result.isVerified());

        // Scattered zones verification
        assertNotNull(result.getScattered());
        assertEquals(8, result.getScattered().size());
        assertEquals("ZONE-A1", result.getScattered().get(0));
        assertEquals("ZONE-A2", result.getScattered().get(1));
        assertEquals("ZONE-B1", result.getScattered().get(2));

        // Gathered reports verification in rank order
        assertNotNull(result.getGathered());
        assertEquals(8, result.getGathered().size());
        assertTrue(result.getGathered().get(0).startsWith("REPORT[R0:ZONE-A1"));
        assertTrue(result.getGathered().get(2).startsWith("REPORT[R1:ZONE-B1"));
        assertTrue(result.getGathered().get(6).startsWith("REPORT[R3:ZONE-D1"));

        // Timeline verification
        List<RankTimelineEntry> timeline = result.getTimeline();
        assertNotNull(timeline);
        assertFalse(timeline.isEmpty());
        assertTrue(timeline.stream().anyMatch(t -> t.getAction().equals("BCAST_DISPATCHED")));
        assertTrue(timeline.stream().anyMatch(t -> t.getAction().equals("SCATTER_RECEIVED")));
        assertTrue(timeline.stream().anyMatch(t -> t.getAction().equals("LOCAL_MISSION_COMPLETED")));
        assertTrue(timeline.stream().anyMatch(t -> t.getAction().equals("GATHER_ASSEMBLED")));

        // Events published
        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_BCAST"), any());
        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_SCATTER"), any());
        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_GATHER"), any());
    }

    @Test
    @DisplayName("runBroadcast: root broadcasts alert text to all ranks")
    void testRunBroadcast() {
        CommRequest req = new CommRequest(3, 1, 0);
        req.setAlertText("COMMAND: High Winds Detected");

        CollectiveResult result = service.runBroadcast(req);

        assertNotNull(result);
        assertEquals("BCAST", result.getOperation());
        assertEquals(3, result.getProcesses());
        assertTrue(result.isSelfCheckPassed());
        assertEquals(3, result.getRankResults().size());

        for (RankExecutionRecord r : result.getRankResults()) {
            assertTrue(r.getSteps().stream().anyMatch(s -> s.contains("Confirmed alert")));
        }

        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_BCAST"), any());
    }

    @Test
    @DisplayName("runScatter: root partitions zones evenly across ranks")
    void testRunScatter() {
        CommRequest req = new CommRequest(4, 2, 0);

        CollectiveResult result = service.runScatter(req);

        assertNotNull(result);
        assertEquals("SCATTER", result.getOperation());
        assertEquals(4, result.getProcesses());
        assertEquals(8, result.getScattered().size());
        assertTrue(result.isSelfCheckPassed());

        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_SCATTER"), any());
    }

    @Test
    @DisplayName("runGather: root collects reports from all ranks in strict rank order")
    void testRunGather() {
        CommRequest req = new CommRequest(3, 2, 0);

        CollectiveResult result = service.runGather(req);

        assertNotNull(result);
        assertEquals("GATHER", result.getOperation());
        assertEquals(3, result.getProcesses());
        assertTrue(result.isSelfCheckPassed());
        assertEquals(6, result.getGathered().size());
        assertTrue(result.getGathered().get(0).contains("R0:"));
        assertTrue(result.getGathered().get(2).contains("R1:"));
        assertTrue(result.getGathered().get(4).contains("R2:"));

        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_GATHER"), any());
    }

    @Test
    @DisplayName("Validation: rejects invalid processes or chunk size")
    void testValidationLimits() {
        // processes < 2
        CommRequest reqTooFewProcesses = new CommRequest(1, 2, 0);
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> service.validateRequest(reqTooFewProcesses));
        assertTrue(ex1.getMessage().contains("between 2 and 16"));

        // processes > 16
        CommRequest reqTooManyProcesses = new CommRequest(17, 2, 0);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> service.validateRequest(reqTooManyProcesses));
        assertTrue(ex2.getMessage().contains("between 2 and 16"));

        // chunkSize < 1
        CommRequest reqZeroChunk = new CommRequest(4, 0, 0);
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () -> service.validateRequest(reqZeroChunk));
        assertTrue(ex3.getMessage().contains("between 1 and 10"));

        // chunkSize > 10
        CommRequest reqTooLargeChunk = new CommRequest(4, 11, 0);
        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class, () -> service.validateRequest(reqTooLargeChunk));
        assertTrue(ex4.getMessage().contains("between 1 and 10"));
    }
}
