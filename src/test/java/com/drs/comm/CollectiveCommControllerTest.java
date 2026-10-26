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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CollectiveCommControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("GET /api/comm/status returns status info")
    void getStatus() throws Exception {
        mvc.perform(get("/api/comm/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experiment").value(9))
                .andExpect(jsonPath("$.defaultProcesses").value(4));
    }

    @Test
    @DisplayName("POST /api/comm/run-all executes full workflow successfully")
    void postRunAll() throws Exception {
        String body = """
                {
                  "numProcesses": 4,
                  "chunkSize": 2,
                  "rootRank": 0,
                  "alertData": [911, 12, 5]
                }
                """;

        mvc.perform(post("/api/comm/run-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("RUN_ALL"))
                .andExpect(jsonPath("$.numProcesses").value(4))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.gatheredData.length()").value(8));
    }

    @Test
    @DisplayName("POST /api/comm/bcast executes broadcast")
    void postBcast() throws Exception {
        String body = """
                {
                  "numProcesses": 3,
                  "rootRank": 0,
                  "alertData": [999, 1, 9]
                }
                """;

        mvc.perform(post("/api/comm/bcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("BCAST"))
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    @DisplayName("POST /api/comm/scatter executes scatter")
    void postScatter() throws Exception {
        String body = """
                {
                  "numProcesses": 4,
                  "chunkSize": 2,
                  "rootRank": 0
                }
                """;

        mvc.perform(post("/api/comm/scatter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("SCATTER"))
                .andExpect(jsonPath("$.chunkSize").value(2));
    }

    @Test
    @DisplayName("POST /api/comm/gather executes gather")
    void postGather() throws Exception {
        String body = """
                {
                  "numProcesses": 3,
                  "chunkSize": 2,
                  "rootRank": 0
                }
                """;

        mvc.perform(post("/api/comm/gather")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("GATHER"))
                .andExpect(jsonPath("$.gatheredData.length()").value(6));
    }
}
