package com.opsvision.incident.controller;

import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.mapper.IncidentMapper;
import com.opsvision.incident.model.IncidentSeverity;
import com.opsvision.incident.service.IncidentDetectionService;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.recovery.mapper.RecoveryMapper;
import com.opsvision.recovery.service.RecoveryRecommendationService;
import com.opsvision.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IncidentController.class)
@Import({IncidentMapper.class, RecoveryMapper.class, GlobalExceptionHandler.class})
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidentDetectionService incidentDetectionService;

    @MockBean
    private RootCauseAnalysisService rootCauseAnalysisService;

    @MockBean
    private RecoveryRecommendationService recoveryRecommendationService;

    @Test
    void detect_noIncident_returnsFlagFalse() throws Exception {
        when(incidentDetectionService.detectFromLiveTelemetry(isNull(), eq("prod"), eq("api")))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/incidents/detect")
                        .param("namespace", "prod")
                        .param("workload", "api")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentDetected").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void detect_withIncident_returnsBody() throws Exception {
        Incident incident = new Incident("Elevated error ratio (api)", IncidentSeverity.HIGH,
                Instant.parse("2026-08-18T12:00:00Z"));
        incident.setNamespace("prod");
        incident.setWorkloadName("api");
        incident.setSummary("errors up");

        when(incidentDetectionService.detectFromLiveTelemetry(eq(1L), isNull(), isNull()))
                .thenReturn(Optional.of(incident));

        mockMvc.perform(post("/api/v1/incidents/detect")
                        .param("deploymentId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentDetected").value(true))
                .andExpect(jsonPath("$.incident.title").value("Elevated error ratio (api)"))
                .andExpect(jsonPath("$.incident.severity").value("HIGH"))
                .andExpect(jsonPath("$.incident.status").value("OPEN"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(incidentDetectionService.getById(42L)).thenThrow(new IncidentNotFoundException(42L));

        mockMvc.perform(get("/api/v1/incidents/42").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Incident Not Found"));
    }

    @Test
    void list_returnsPage() throws Exception {
        Incident incident = new Incident("t", IncidentSeverity.LOW, Instant.parse("2026-08-18T12:00:00Z"));
        when(incidentDetectionService.list(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(incident)));

        mockMvc.perform(get("/api/v1/incidents").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("t"));
    }
}
