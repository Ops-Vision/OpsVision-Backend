package com.opsvision.observability.controller;

import com.opsvision.observability.mapper.TelemetryMapper;
import com.opsvision.observability.model.ServiceMetricsSnapshot;
import com.opsvision.observability.model.TelemetrySnapshot;
import com.opsvision.observability.service.TelemetryCollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TelemetryController.class)
@Import(TelemetryMapper.class)
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelemetryCollectionService telemetryCollectionService;

    @Test
    void getTelemetry_returnsSnapshot() throws Exception {
        when(telemetryCollectionService.collect(eq("prod"), eq("api"))).thenReturn(
                new TelemetrySnapshot(
                        "prod",
                        "api",
                        Instant.parse("2026-08-18T12:00:00Z"),
                        List.of(),
                        List.of(),
                        List.of(),
                        ServiceMetricsSnapshot.empty(),
                        false,
                        false,
                        List.of("Observability collection is disabled")
                )
        );

        mockMvc.perform(get("/api/v1/observability/telemetry")
                        .param("namespace", "prod")
                        .param("workload", "api")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.namespace").value("prod"))
                .andExpect(jsonPath("$.workloadName").value("api"))
                .andExpect(jsonPath("$.kubernetesAvailable").value(false))
                .andExpect(jsonPath("$.notes[0]").value("Observability collection is disabled"));
    }

    @Test
    void getTelemetry_withoutParams_usesNulls() throws Exception {
        when(telemetryCollectionService.collect(isNull(), isNull())).thenReturn(
                new TelemetrySnapshot(
                        "default",
                        null,
                        Instant.parse("2026-08-18T12:00:00Z"),
                        List.of(),
                        List.of(),
                        List.of(),
                        ServiceMetricsSnapshot.empty(),
                        false,
                        false,
                        List.of()
                )
        );

        mockMvc.perform(get("/api/v1/observability/telemetry")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.namespace").value("default"));
    }
}
