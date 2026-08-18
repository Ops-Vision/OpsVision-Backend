package com.opsvision.incident.controller;

import com.opsvision.common.exception.GlobalExceptionHandler;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.mapper.IncidentMapper;
import com.opsvision.incident.service.IncidentDetectionService;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.recovery.mapper.RecoveryMapper;
import com.opsvision.recovery.model.RecoveryAction;
import com.opsvision.recovery.model.RecoveryRecommendationResult;
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
class IncidentRecoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidentDetectionService incidentDetectionService;

    @MockBean
    private RootCauseAnalysisService rootCauseAnalysisService;

    @MockBean
    private RecoveryRecommendationService recoveryRecommendationService;

    @Test
    void recovery_returnsRecommendation() throws Exception {
        RecoveryRecommendationResult result = new RecoveryRecommendationResult(
                1L,
                Instant.parse("2026-08-18T12:00:00Z"),
                RecoveryAction.ROLLBACK,
                "Error rate increased after v2; roll back to v1",
                "v1abc",
                9L,
                "v2def",
                "DEPLOYMENT_REGRESSION",
                0.91,
                true,
                RecoveryRecommendationResult.EXECUTION_MODE_RECOMMENDATION_ONLY,
                List.of("errors up"),
                List.of("Recommendation only")
        );
        when(recoveryRecommendationService.recommend(1L)).thenReturn(result);

        mockMvc.perform(get("/api/v1/incidents/1/recovery").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("ROLLBACK"))
                .andExpect(jsonPath("$.targetVersion").value("v1abc"))
                .andExpect(jsonPath("$.requiresHumanApproval").value(true))
                .andExpect(jsonPath("$.executionMode").value("RECOMMENDATION_ONLY"));
    }

    @Test
    void recovery_notFound_returns404() throws Exception {
        when(recoveryRecommendationService.recommend(42L)).thenThrow(new IncidentNotFoundException(42L));

        mockMvc.perform(get("/api/v1/incidents/42/recovery").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Incident Not Found"));
    }
}
