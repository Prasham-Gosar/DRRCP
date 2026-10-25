package com.drs.core.controller;

import com.drs.core.model.NodeStatusRepository;
import org.junit.jupiter.api.BeforeEach;
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
class CoordinatorControllerTest {

    @Autowired MockMvc mvc;
    @Autowired NodeStatusRepository nodes;

    @BeforeEach
    void clean() {
        nodes.deleteAll();
    }

    @Test
    void registerThenReadBackOverRest() throws Exception {
        String body = """
                {"nodeId":"UAV-01","batteryLevel":80,"signalStrength":70,"nodeType":"UAV","clusterId":"Cluster-A"}""";

        mvc.perform(post("/api/coordinator/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STABLE"))
                .andExpect(jsonPath("$.connectivityScore").value(220));

        mvc.perform(get("/api/coordinator/nodes/UAV-01/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STABLE"));

        mvc.perform(get("/api/coordinator/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nodeId").value("UAV-01"));
    }

    @Test
    void invalidRegistrationIsRejectedWith400() throws Exception {
        String body = """
                {"nodeId":"","batteryLevel":150,"signalStrength":-5,"nodeType":"UAV","clusterId":"Cluster-A"}""";

        mvc.perform(post("/api/coordinator/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownNodeIs404() throws Exception {
        mvc.perform(get("/api/coordinator/nodes/ghost")).andExpect(status().isNotFound());
        mvc.perform(get("/api/coordinator/nodes/ghost/state")).andExpect(status().isNotFound());
    }

    @Test
    void heartbeatAndTimeEndpointsRespond() throws Exception {
        mvc.perform(post("/api/coordinator/heartbeat/UAV-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value("UAV-01"));

        mvc.perform(get("/api/coordinator/time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").isNumber());
    }
}
