package com.drs.comm.controller;

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
class MatrixControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("GET /api/matrix/status returns experiment 10 info")
    void testGetStatus() throws Exception {
        mvc.perform(get("/api/matrix/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experiment").value(10))
                .andExpect(jsonPath("$.defaultN").value(400))
                .andExpect(jsonPath("$.maxN").value(800));
    }

    @Test
    @DisplayName("POST /api/matrix/run benchmarks sequential vs parallel matrix multiplication")
    void testPostRun() throws Exception {
        String body = """
                {
                  "n": 60,
                  "threads": 2
                }
                """;

        mvc.perform(post("/api/matrix/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").value(60))
                .andExpect(jsonPath("$.threads").value(2))
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.sampleResult").isArray())
                .andExpect(jsonPath("$.speedup").isNumber())
                .andExpect(jsonPath("$.steps").isArray());
    }

    @Test
    @DisplayName("POST /api/matrix/sweep benchmarks across thread powers of two")
    void testPostSweep() throws Exception {
        String body = """
                {
                  "n": 50,
                  "maxThreads": 4
                }
                """;

        mvc.perform(post("/api/matrix/sweep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").value(50))
                .andExpect(jsonPath("$.maxThreads").value(4))
                .andExpect(jsonPath("$.runs").isArray())
                .andExpect(jsonPath("$.bestThreads").isNumber())
                .andExpect(jsonPath("$.bestSpeedup").isNumber());
    }

    @Test
    @DisplayName("POST /api/matrix/run rejects dimension n > 800 with 400 Bad Request")
    void testPostRunExcessiveN() throws Exception {
        String body = """
                {
                  "n": 850,
                  "threads": 4
                }
                """;

        mvc.perform(post("/api/matrix/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.correct").value(false));
    }

    @Test
    @DisplayName("POST /api/matrix/run rejects dimension n < 2 with 400 Bad Request")
    void testPostRunTooSmallN() throws Exception {
        String body = """
                {
                  "n": 1,
                  "threads": 2
                }
                """;

        mvc.perform(post("/api/matrix/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }
}
