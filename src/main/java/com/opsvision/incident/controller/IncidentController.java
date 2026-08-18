package com.opsvision.incident.controller;

import com.opsvision.github.dto.IncidentGitHubIssueResponse;
import com.opsvision.github.service.IncidentGitHubIssueService;
import com.opsvision.incident.dto.IncidentDetectionResponse;
import com.opsvision.incident.dto.IncidentResponse;
import com.opsvision.incident.dto.RootCauseAnalysisResponse;
import com.opsvision.incident.mapper.IncidentMapper;
import com.opsvision.incident.service.IncidentDetectionService;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.recovery.dto.RecoveryRecommendationResponse;
import com.opsvision.recovery.mapper.RecoveryMapper;
import com.opsvision.recovery.service.RecoveryRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/incidents", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Incidents", description = "Incident detection, timeline, RCA, recovery, and GitHub issues")
public class IncidentController {

    private final IncidentDetectionService incidentDetectionService;
    private final RootCauseAnalysisService rootCauseAnalysisService;
    private final RecoveryRecommendationService recoveryRecommendationService;
    private final IncidentGitHubIssueService incidentGitHubIssueService;
    private final IncidentMapper incidentMapper;
    private final RecoveryMapper recoveryMapper;

    public IncidentController(
            IncidentDetectionService incidentDetectionService,
            RootCauseAnalysisService rootCauseAnalysisService,
            RecoveryRecommendationService recoveryRecommendationService,
            IncidentGitHubIssueService incidentGitHubIssueService,
            IncidentMapper incidentMapper,
            RecoveryMapper recoveryMapper
    ) {
        this.incidentDetectionService = incidentDetectionService;
        this.rootCauseAnalysisService = rootCauseAnalysisService;
        this.recoveryRecommendationService = recoveryRecommendationService;
        this.incidentGitHubIssueService = incidentGitHubIssueService;
        this.incidentMapper = incidentMapper;
        this.recoveryMapper = recoveryMapper;
    }

    @PostMapping("/detect")
    @Operation(summary = "Collect telemetry and detect an incident with correlated timeline")
    public IncidentDetectionResponse detect(
            @RequestParam(required = false) Long deploymentId,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String workload
    ) {
        return incidentDetectionService.detectFromLiveTelemetry(deploymentId, namespace, workload)
                .map(incidentMapper::toResponse)
                .map(IncidentDetectionResponse::of)
                .orElseGet(() -> IncidentDetectionResponse.none(
                        "No incident-worthy signals in current telemetry"
                ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incident by id including timeline")
    public IncidentResponse getById(@PathVariable Long id) {
        return incidentMapper.toResponse(incidentDetectionService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List recent incidents")
    public Page<IncidentResponse> list(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return incidentDetectionService.list(pageable).map(incidentMapper::toResponse);
    }

    @GetMapping("/by-deployment/{deploymentId}")
    @Operation(summary = "List incidents linked to a deployment")
    public List<IncidentResponse> byDeployment(@PathVariable Long deploymentId) {
        return incidentDetectionService.listByDeployment(deploymentId).stream()
                .map(incidentMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}/rca")
    @Operation(summary = "Run deterministic root-cause analysis for an incident")
    public RootCauseAnalysisResponse analyzeRootCause(@PathVariable Long id) {
        return incidentMapper.toRcaResponse(rootCauseAnalysisService.analyze(id));
    }

    @GetMapping("/{id}/recovery")
    @Operation(summary = "Recommend recovery action (recommendation only; does not execute)")
    public RecoveryRecommendationResponse recommendRecovery(@PathVariable Long id) {
        return recoveryMapper.toResponse(recoveryRecommendationService.recommend(id));
    }

    @PostMapping("/{id}/github-issue")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Create a GitHub issue for the incident (idempotent; prevents duplicates)")
    public IncidentGitHubIssueResponse createGitHubIssue(@PathVariable Long id) {
        return incidentGitHubIssueService.createOrGetIssue(id);
    }
}
