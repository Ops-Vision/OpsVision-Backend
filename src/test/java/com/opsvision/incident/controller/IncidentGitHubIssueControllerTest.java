package com.opsvision.incident.controller;

import com.opsvision.common.exception.GlobalExceptionHandler;
import com.opsvision.github.dto.IncidentGitHubIssueResponse;
import com.opsvision.github.service.IncidentGitHubIssueService;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.mapper.IncidentMapper;
import com.opsvision.incident.service.IncidentDetectionService;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.postmortem.mapper.PostmortemMapper;
import com.opsvision.postmortem.service.PostmortemService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IncidentController.class)
@Import({IncidentMapper.class, RecoveryMapper.class, PostmortemMapper.class, GlobalExceptionHandler.class})
class IncidentGitHubIssueControllerTest {

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
    void createGitHubIssue_returnsBody() throws Exception {
        when(incidentGitHubIssueService.createOrGetIssue(1L)).thenReturn(new IncidentGitHubIssueResponse(
                1L,
                42,
                "https://github.com/acme/api/issues/42",
                "[OpsVision][HIGH] title",
                "open",
                true,
                false,
                Instant.parse("2026-08-18T12:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/incidents/1/github-issue").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(1))
                .andExpect(jsonPath("$.issueNumber").value(42))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.duplicatePrevented").value(false))
                .andExpect(jsonPath("$.issueUrl").value("https://github.com/acme/api/issues/42"));
    }

    @Test
    void createGitHubIssue_notFound_returns404() throws Exception {
        when(incidentGitHubIssueService.createOrGetIssue(99L)).thenThrow(new IncidentNotFoundException(99L));

        mockMvc.perform(post("/api/v1/incidents/99/github-issue").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Incident Not Found"));
    }
}
