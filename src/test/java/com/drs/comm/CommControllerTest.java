package com.drs.comm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("GET /api/comm/status returns experiment status and defaults")
    void testGetStatus() throws Exception {
        mvc.perform(get("/api/comm/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experiment").value(9))
                .andExpect(jsonPath("$.defaultProcesses").value(4))
                .andExpect(jsonPath("$.defaultChunkSize").value(2));
    }

    @Test
    @DisplayName("POST /api/comm/run-all executes full workflow successfully with self-check")
    void testPostRunAll() throws Exception {
        String body = """
                {
                  "processes": 4,
                  "chunkSize": 2,
                  "rootRank": 0,
                  "alertText": "EVACUATE: Severe Dam Overflow"
                }
                """;

        mvc.perform(post("/api/comm/run-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("RUN_ALL"))
                .andExpect(jsonPath("$.processes").value(4))
                .andExpect(jsonPath("$.selfCheckPassed").value(true))
                .andExpect(jsonPath("$.gathered.length()").value(8))
                .andExpect(jsonPath("$.scattered.length()").value(8))
                .andExpect(jsonPath("$.timeline").isArray());
    }

    @Test
    @DisplayName("POST /api/comm/bcast executes broadcast")
    void testPostBcast() throws Exception {
        String body = """
                {
                  "processes": 3,
                  "rootRank": 0,
                  "alertText": "TEST ALERT: Radio Silence"
                }
                """;

        mvc.perform(post("/api/comm/bcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("BCAST"))
                .andExpect(jsonPath("$.selfCheckPassed").value(true));
    }

    @Test
    @DisplayName("POST /api/comm/scatter executes scatter")
    void testPostScatter() throws Exception {
        String body = """
                {
                  "processes": 4,
                  "chunkSize": 2,
                  "rootRank": 0
                }
                """;

        mvc.perform(post("/api/comm/scatter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("SCATTER"))
                .andExpect(jsonPath("$.scattered.length()").value(8))
                .andExpect(jsonPath("$.chunkSize").value(2));
    }

    @Test
    @DisplayName("POST /api/comm/gather executes gather")
    void testPostGather() throws Exception {
        String body = """
                {
                  "processes": 3,
                  "chunkSize": 2,
                  "rootRank": 0
                }
                """;

        mvc.perform(post("/api/comm/gather")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("GATHER"))
                .andExpect(jsonPath("$.gathered.length()").value(6))
                .andExpect(jsonPath("$.selfCheckPassed").value(true));
    }

    @Test
    @DisplayName("POST /api/comm/run-all returns 400 Bad Request when processes < 2")
    void testInvalidProcessesLow() throws Exception {
        String body = """
                {
                  "processes": 1,
                  "chunkSize": 2
                }
                """;

        mvc.perform(post("/api/comm/run-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.selfCheckPassed").value(false));
    }

    @Test
    @DisplayName("POST /api/comm/run-all returns 400 Bad Request when processes > 16")
    void testInvalidProcessesHigh() throws Exception {
        String body = """
                {
                  "processes": 17,
                  "chunkSize": 2
                }
                """;

        mvc.perform(post("/api/comm/run-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    @DisplayName("POST /api/comm/run-all returns 400 Bad Request when chunkSize > 10")
    void testInvalidChunkSizeHigh() throws Exception {
        String body = """
                {
                  "processes": 4,
                  "chunkSize": 11
                }
                """;

        mvc.perform(post("/api/comm/run-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }
}
