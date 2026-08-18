package com.opsvision.incident.controller;

import com.opsvision.common.exception.GlobalExceptionHandler;
import com.opsvision.github.service.IncidentGitHubIssueService;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.mapper.IncidentMapper;
import com.opsvision.incident.service.IncidentDetectionService;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.postmortem.mapper.PostmortemMapper;
import com.opsvision.postmortem.model.PostmortemResult;
import com.opsvision.postmortem.service.PostmortemService;
import com.opsvision.recovery.mapper.RecoveryMapper;
import com.opsvision.recovery.model.RecoveryAction;
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
@Import({IncidentMapper.class, RecoveryMapper.class, PostmortemMapper.class, GlobalExceptionHandler.class})
class IncidentPostmortemControllerTest {

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

    @MockBean
    private PostmortemService postmortemService;

    @Test
    void postmortem_returnsDraft() throws Exception {
        PostmortemResult result = new PostmortemResult(
                1L,
                Instant.parse("2026-08-18T12:00:00Z"),
                PostmortemResult.METHOD,
                "Postmortem: [HIGH] Elevated errors",
                "Executive summary text",
                "Severity HIGH; environment prod.",
                "HIGH",
                "OPEN",
                "prod",
                "prod",
                "api",
                "abc123",
                9L,
                Instant.parse("2026-08-18T11:00:00Z"),
                Instant.parse("2026-08-18T11:00:00Z"),
                null,
                null,
                "Recent deployment likely introduced elevated error rate",
                "DEPLOYMENT_REGRESSION",
                0.91,
                List.of("DEPLOYMENT_REGRESSION: cause"),
                List.of(new PostmortemResult.TimelineHighlight(
                        Instant.parse("2026-08-18T11:01:00Z"),
                        "METRIC",
                        "Error spike",
                        "ratio=0.1"
                )),
                List.of("Timeline correlated"),
                List.of("Error spike after deploy"),
                List.of("Prepare rollback"),
                RecoveryAction.ROLLBACK,
                "Roll back",
                "deadbeef",
                true,
                List.of("Human review required"),
                "# Postmortem\n"
        );
        when(postmortemService.generate(1L)).thenReturn(result);

        mockMvc.perform(get("/api/v1/incidents/1/postmortem").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(1))
                .andExpect(jsonPath("$.method").value("deterministic-template"))
                .andExpect(jsonPath("$.topRcaCategory").value("DEPLOYMENT_REGRESSION"))
                .andExpect(jsonPath("$.recommendedRecoveryAction").value("ROLLBACK"))
                .andExpect(jsonPath("$.timelineHighlights[0].title").value("Error spike"))
                .andExpect(jsonPath("$.markdownBody").exists());
    }

    @Test
    void postmortem_notFound_returns404() throws Exception {
        when(postmortemService.generate(42L)).thenThrow(new IncidentNotFoundException(42L));

        mockMvc.perform(get("/api/v1/incidents/42/postmortem").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Incident Not Found"));
    }
}
