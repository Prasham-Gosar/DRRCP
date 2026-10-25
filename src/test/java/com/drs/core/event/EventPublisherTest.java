package com.drs.core.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventPublisherTest {

    @Autowired EventPublisher publisher;
    @Autowired EventLogRepository repo;
    @Autowired MockMvc mvc;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void publishPersistsAnEventLogRow() {
        publisher.publish("election", "LEADER_ELECTED", Map.of("leader", "UAV-02"));

        List<EventLog> all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals("election", all.get(0).getModule());
        assertEquals("LEADER_ELECTED", all.get(0).getType());
        assertTrue(all.get(0).getPayload().contains("UAV-02"));
        assertNotNull(all.get(0).getTimestamp());
    }

    @Test
    void historyIsNewestFirstAndRespectsLimit() {
        for (int i = 1; i <= 5; i++) publisher.publish("test", "E" + i, Map.of("i", i));

        List<EventLog> two = publisher.history(2);

        assertEquals(2, two.size());
        assertEquals("E5", two.get(0).getType());
        assertEquals("E4", two.get(1).getType());
    }

    @Test
    void historyWithAbsurdLimitsIsClampedNotCrashing() {
        publisher.publish("test", "ONLY", Map.of());
        assertEquals(1, publisher.history(0).size());      // clamped up to 1
        assertEquals(1, publisher.history(100000).size()); // clamped down to 500
    }

    @Test
    void subscribeRegistersANewEmitter() {
        int before = publisher.subscriberCount();
        var emitter = publisher.subscribe();
        assertNotNull(emitter);
        assertEquals(before + 1, publisher.subscriberCount());
    }

    @Test
    void publishWithNoSubscribersDoesNotThrow() {
        assertDoesNotThrow(() -> publisher.publish("test", "LONELY", "hello"));
    }

    @Test
    void historyEndpointReturnsJson() throws Exception {
        publisher.publish("coordinator", "NODE_REGISTERED", Map.of("nodeId", "UAV-01"));

        mvc.perform(get("/api/events/history").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].module").value("coordinator"))
                .andExpect(jsonPath("$[0].type").value("NODE_REGISTERED"));
    }
}
