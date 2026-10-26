package com.drs.comm.service;

import com.drs.comm.model.CollectiveResult;
import com.drs.comm.model.CommRequest;
import com.drs.comm.model.RankExecutionRecord;
import com.drs.core.event.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link CollectiveCommService}.
 */
@ExtendWith(MockitoExtension.class)
class CollectiveCommServiceTest {

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private CollectiveCommService service;

    @Test
    @DisplayName("runAll executes full workflow and verifies gather integrity")
    void runAllSuccessful() {
        CommRequest req = new CommRequest(4, 2, 0);
        req.setAlertData(new int[]{911, 12, 5});

        CollectiveResult result = service.runAll(req);

        assertNotNull(result);
        assertEquals("RUN_ALL", result.getOperation());
        assertEquals(4, result.getNumProcesses());
        assertEquals(2, result.getChunkSize());
        assertEquals(0, result.getRootRank());
        assertTrue(result.isVerified(), "Full collective comms run should be verified");
        assertNotNull(result.getGatheredData());
        assertEquals(8, result.getGatheredData().length);

        // Check verification formula: gathered[i] == (i + 1) * 10 + (i / chunk)
        int[] expectedGathered = new int[]{10, 20, 31, 41, 52, 62, 73, 83};
        assertArrayEquals(expectedGathered, result.getGatheredData());

        // Check per-rank records
        assertEquals(4, result.getRankResults().size());
        for (int r = 0; r < 4; r++) {
            RankExecutionRecord rec = result.getRankResults().get(r);
            assertEquals(r, rec.getRank());
            assertArrayEquals(new int[]{911, 12, 5}, rec.getReceivedAlert());
            assertNotNull(rec.getReceivedChunk());
            assertEquals(2, rec.getReceivedChunk().length);
            assertNotNull(rec.getDeliveredChunk());
            assertEquals(2, rec.getDeliveredChunk().length);
        }

        // Verify live events published
        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_BCAST"), any());
        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_SCATTER"), any());
        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_GATHER"), any());
    }

    @Test
    @DisplayName("runBroadcast executes broadcast across all ranks")
    void runBroadcast() {
        CommRequest req = new CommRequest(3, 1, 0);
        req.setAlertData(new int[]{100, 200, 300});

        CollectiveResult result = service.runBroadcast(req);

        assertNotNull(result);
        assertEquals("BCAST", result.getOperation());
        assertEquals(3, result.getNumProcesses());
        assertTrue(result.isVerified());
        assertEquals(3, result.getRankResults().size());

        for (RankExecutionRecord r : result.getRankResults()) {
            assertArrayEquals(new int[]{100, 200, 300}, r.getReceivedAlert());
        }

        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_BCAST"), any());
    }

    @Test
    @DisplayName("runScatter scatters equal chunks to ranks")
    void runScatter() {
        CommRequest req = new CommRequest(4, 2, 0);

        CollectiveResult result = service.runScatter(req);

        assertNotNull(result);
        assertEquals("SCATTER", result.getOperation());
        assertEquals(4, result.getNumProcesses());
        assertEquals(2, result.getChunkSize());
        assertEquals(4, result.getRankResults().size());

        assertArrayEquals(new int[]{10, 20}, result.getRankResults().get(0).getReceivedChunk());
        assertArrayEquals(new int[]{30, 40}, result.getRankResults().get(1).getReceivedChunk());
        assertArrayEquals(new int[]{50, 60}, result.getRankResults().get(2).getReceivedChunk());
        assertArrayEquals(new int[]{70, 80}, result.getRankResults().get(3).getReceivedChunk());

        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_SCATTER"), any());
    }

    @Test
    @DisplayName("runGather collects chunks from ranks in rank order")
    void runGather() {
        CommRequest req = new CommRequest(3, 2, 0);

        CollectiveResult result = service.runGather(req);

        assertNotNull(result);
        assertEquals("GATHER", result.getOperation());
        assertEquals(3, result.getNumProcesses());
        assertTrue(result.isVerified());
        assertNotNull(result.getGatheredData());
        assertEquals(6, result.getGatheredData().length);

        verify(eventPublisher, atLeastOnce()).publish(eq("comm"), eq("COMM_GATHER"), any());
    }
}
