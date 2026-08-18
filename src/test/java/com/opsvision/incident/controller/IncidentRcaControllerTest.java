package com.opsvision.incident.controller;

import com.opsvision.common.exception.GlobalExceptionHandler;
import com.opsvision.github.service.IncidentGitHubIssueService;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.mapper.IncidentMapper;
import com.opsvision.incident.model.ProbableCause;
import com.opsvision.incident.model.RootCauseAnalysisResult;
import com.opsvision.incident.service.IncidentDetectionService;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.recovery.mapper.RecoveryMapper;
import com.opsvision.recovery.service.RecoveryRecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IncidentController.class)
@Import({IncidentMapper.class, RecoveryMapper.class, GlobalExceptionHandler.class})
class IncidentRcaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidentDetectionService incidentDetectionService;

    @MockBean
    private RootCauseAnalysisService rootCauseAnalysisService;

    @MockBean
    private RecoveryRecommendationService recoveryRecommendationService;

    @MockBean
    private IncidentGitHubIssueService incidentGitHubIssueService;

    @Test
    void rca_returnsRankedCauses() throws Exception {
        RootCauseAnalysisResult result = new RootCauseAnalysisResult(
                1L,
                Instant.parse("2026-08-18T12:00:00Z"),
                "deterministic-correlation",
                "summary",
                List.of(new ProbableCause(
                        "Recent deployment likely introduced elevated error rate or reduced availability",
                        0.91,
                        "DEPLOYMENT_REGRESSION",
                        List.of("Error rate increased two minutes after deployment")
                )),
                List.of()
        );
        when(rootCauseAnalysisService.analyze(1L)).thenReturn(result);

        mockMvc.perform(get("/api/v1/incidents/1/rca").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(1))
                .andExpect(jsonPath("$.method").value("deterministic-correlation"))
                .andExpect(jsonPath("$.probableCauses[0].category").value("DEPLOYMENT_REGRESSION"))
                .andExpect(jsonPath("$.probableCauses[0].confidence").value(0.91))
                .andExpect(jsonPath("$.probableCauses[0].evidence[0]").exists());
    }

    @Test
    void rca_notFound_returns404() throws Exception {
        when(rootCauseAnalysisService.analyze(42L)).thenThrow(new IncidentNotFoundException(42L));

        mockMvc.perform(get("/api/v1/incidents/42/rca").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Incident Not Found"));
    }
}
