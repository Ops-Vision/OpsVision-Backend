package com.opsvision.deployment.controller;

import com.opsvision.common.exception.GlobalExceptionHandler;
import com.opsvision.deployment.dto.ConfidenceScoreResponse;
import com.opsvision.deployment.dto.DeploymentAnalysisResponse;
import com.opsvision.deployment.dto.DeploymentResponse;
import com.opsvision.deployment.dto.PolicyDecisionResponse;
import com.opsvision.deployment.dto.RepositorySummaryDto;
import com.opsvision.deployment.dto.ScoreFactorResponse;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.deployment.service.DeploymentAnalysisService;
import com.opsvision.evidence.exception.DeploymentNotFoundException;
import com.opsvision.policy.model.PolicyDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeploymentAnalysisController.class)
@Import(GlobalExceptionHandler.class)
class DeploymentAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeploymentAnalysisService analysisService;

    @Test
    void analyzeReturnsCreatedWithScoreAndPolicy() throws Exception {
        DeploymentAnalysisResponse response = sampleAnalysis(1L);
        when(analysisService.analyze(any())).thenReturn(response);

        String body = """
                {
                  "owner": "acme",
                  "repository": "payments",
                  "commitSha": "abc123",
                  "branch": "main",
                  "environment": "staging",
                  "evidence": [
                    {
                      "evidenceType": "BUILD",
                      "status": "PASSED",
                      "source": "ci",
                      "summary": "ok"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deployment.id").value(1))
                .andExpect(jsonPath("$.score.score").value(90))
                .andExpect(jsonPath("$.policy.decision").value("DEPLOY"));
    }

    @Test
    void analyzeValidationFailureReturnsProblemDetail() throws Exception {
        String body = """
                {
                  "owner": "",
                  "repository": "payments",
                  "commitSha": "abc",
                  "branch": "main",
                  "environment": "staging"
                }
                """;

        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.owner").exists());
    }

    @Test
    void getDeploymentNotFoundReturns404Problem() throws Exception {
        when(analysisService.getDeployment(99L)).thenThrow(new DeploymentNotFoundException(99L));

        mockMvc.perform(get("/api/v1/deployments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Deployment Not Found"))
                .andExpect(jsonPath("$.deploymentId").value(99));
    }

    @Test
    void listRecentReturnsPage() throws Exception {
        DeploymentResponse item = sampleDeployment(2L);
        when(analysisService.listRecent(0, 20))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/deployments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getScoreAndPolicy() throws Exception {
        when(analysisService.getScore(eq(5L))).thenReturn(
                new ConfidenceScoreResponse(5L, 75, List.of(
                        new ScoreFactorResponse("build", 20, 20, "Build passed")
                ))
        );
        when(analysisService.getPolicy(eq(5L))).thenReturn(
                new PolicyDecisionResponse(5L, PolicyDecision.REVIEW, List.of("needs review"), 75)
        );

        mockMvc.perform(get("/api/v1/deployments/5/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(75));

        mockMvc.perform(get("/api/v1/deployments/5/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REVIEW"));
    }

    private static DeploymentAnalysisResponse sampleAnalysis(Long id) {
        return new DeploymentAnalysisResponse(
                sampleDeployment(id),
                List.of(),
                List.of(),
                new ConfidenceScoreResponse(id, 90, List.of(
                        new ScoreFactorResponse("build", 20, 20, "Build passed")
                )),
                new PolicyDecisionResponse(id, PolicyDecision.DEPLOY, List.of("ok"), 90)
        );
    }

    private static DeploymentResponse sampleDeployment(Long id) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new DeploymentResponse(
                id,
                new RepositorySummaryDto(10L, "acme", "payments", "acme/payments", "https://github.com/acme/payments"),
                "abc123",
                "main",
                "staging",
                DeploymentStatus.SUCCEEDED,
                "CI",
                1L,
                "https://example.com/run/1",
                null,
                now,
                now
        );
    }
}
