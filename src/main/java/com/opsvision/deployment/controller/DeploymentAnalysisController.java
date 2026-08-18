package com.opsvision.deployment.controller;

import com.opsvision.deployment.dto.AnalyzeDeploymentRequest;
import com.opsvision.deployment.dto.ConfidenceScoreResponse;
import com.opsvision.deployment.dto.DeploymentAnalysisResponse;
import com.opsvision.deployment.dto.DeploymentResponse;
import com.opsvision.deployment.dto.EvidenceResponse;
import com.opsvision.deployment.dto.FindingResponse;
import com.opsvision.deployment.dto.PolicyDecisionResponse;
import com.opsvision.deployment.service.DeploymentAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/deployments", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Deployments", description = "Deployment analysis, scoring, and policy decisions")
public class DeploymentAnalysisController {

    private final DeploymentAnalysisService analysisService;

    public DeploymentAnalysisController(DeploymentAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update a deployment, ingest optional evidence, score and evaluate policy")
    public ResponseEntity<DeploymentAnalysisResponse> analyze(
            @Valid @RequestBody AnalyzeDeploymentRequest request
    ) {
        DeploymentAnalysisResponse body = analysisService.analyze(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/analyze")
    @Operation(summary = "Re-run confidence score and policy evaluation for an existing deployment")
    public DeploymentAnalysisResponse reanalyze(@PathVariable("id") Long id) {
        return analysisService.reanalyze(id);
    }

    @GetMapping
    @Operation(summary = "List recent deployments")
    public Page<DeploymentResponse> listRecent(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return analysisService.listRecent(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get deployment metadata")
    public DeploymentResponse getDeployment(@PathVariable("id") Long id) {
        return analysisService.getDeployment(id);
    }

    @GetMapping("/{id}/analysis")
    @Operation(summary = "Get full analysis (evidence, findings, score, policy)")
    public DeploymentAnalysisResponse getAnalysis(@PathVariable("id") Long id) {
        return analysisService.getAnalysis(id);
    }

    @GetMapping("/{id}/evidence")
    @Operation(summary = "List evidence for a deployment")
    public List<EvidenceResponse> listEvidence(@PathVariable("id") Long id) {
        return analysisService.listEvidence(id);
    }

    @GetMapping("/{id}/findings")
    @Operation(summary = "List findings for a deployment")
    public List<FindingResponse> listFindings(@PathVariable("id") Long id) {
        return analysisService.listFindings(id);
    }

    @GetMapping("/{id}/score")
    @Operation(summary = "Get deployment confidence score breakdown")
    public ConfidenceScoreResponse getScore(@PathVariable("id") Long id) {
        return analysisService.getScore(id);
    }

    @GetMapping("/{id}/policy")
    @Operation(summary = "Get deployment policy decision")
    public PolicyDecisionResponse getPolicy(@PathVariable("id") Long id) {
        return analysisService.getPolicy(id);
    }
}
