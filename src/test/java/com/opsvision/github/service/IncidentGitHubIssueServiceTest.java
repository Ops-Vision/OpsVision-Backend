package com.opsvision.github.service;

import com.opsvision.deployment.entity.Deployment;
import com.opsvision.deployment.entity.ProjectRepository;
import com.opsvision.deployment.model.DeploymentStatus;
import com.opsvision.github.client.GitHubApiClient;
import com.opsvision.github.client.dto.GitHubIssueCreateRequest;
import com.opsvision.github.client.dto.GitHubIssueResponse;
import com.opsvision.github.client.dto.GitHubIssueSearchResponse;
import com.opsvision.github.config.GitHubProperties;
import com.opsvision.github.dto.IncidentGitHubIssueResponse;
import com.opsvision.incident.entity.Incident;
import com.opsvision.incident.exception.IncidentNotFoundException;
import com.opsvision.incident.model.IncidentSeverity;
import com.opsvision.incident.model.ProbableCause;
import com.opsvision.incident.model.RootCauseAnalysisResult;
import com.opsvision.incident.repository.IncidentRepository;
import com.opsvision.incident.service.RootCauseAnalysisService;
import com.opsvision.recovery.model.RecoveryAction;
import com.opsvision.recovery.model.RecoveryRecommendationResult;
import com.opsvision.recovery.service.RecoveryRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentGitHubIssueServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private GitHubApiClient gitHubApiClient;
    @Mock
    private RootCauseAnalysisService rootCauseAnalysisService;
    @Mock
    private RecoveryRecommendationService recoveryRecommendationService;

    private GitHubProperties properties;
    private IncidentGitHubIssueService service;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
        properties.setOwner("acme");
        properties.setRepository("api");
        service = new IncidentGitHubIssueService(
                incidentRepository,
                gitHubApiClient,
                properties,
                rootCauseAnalysisService,
                recoveryRecommendationService
        );
    }

    @Test
    void createOrGetIssue_createsIssueAndPersistsLink() {
        Incident incident = sampleIncident(42L);
        when(incidentRepository.findByIdWithTimeline(42L)).thenReturn(Optional.of(incident));
        when(gitHubApiClient.searchIssues(anyString(), anyInt()))
                .thenReturn(new GitHubIssueSearchResponse(0, false, List.of()));

        RootCauseAnalysisResult rca = new RootCauseAnalysisResult(
                42L,
                Instant.parse("2026-08-18T12:00:00Z"),
                "deterministic-correlation",
                "summary",
                List.of(new ProbableCause("deploy regression", 0.9, "DEPLOYMENT_REGRESSION", List.of("errors up"))),
                List.of()
        );
        when(rootCauseAnalysisService.analyzeIncident(incident)).thenReturn(rca);
        when(recoveryRecommendationService.recommendFrom(eq(incident), eq(rca)))
                .thenReturn(new RecoveryRecommendationResult(
                        42L,
                        Instant.parse("2026-08-18T12:01:00Z"),
                        RecoveryAction.ROLLBACK,
                        "roll back",
                        "v1",
                        1L,
                        "abc123",
                        "DEPLOYMENT_REGRESSION",
                        0.9,
                        true,
                        RecoveryRecommendationResult.EXECUTION_MODE_RECOMMENDATION_ONLY,
                        List.of("e1"),
                        List.of("n1")
                ));

        when(gitHubApiClient.createIssue(eq("acme"), eq("api"), any(GitHubIssueCreateRequest.class)))
                .thenReturn(new GitHubIssueResponse(
                        999L,
                        77,
                        "title",
                        "open",
                        "https://github.com/acme/api/issues/77",
                        "body",
                        "2026-08-18T12:02:00Z"
                ));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentGitHubIssueResponse response = service.createOrGetIssue(42L);

        assertThat(response.created()).isTrue();
        assertThat(response.duplicatePrevented()).isFalse();
        assertThat(response.issueNumber()).isEqualTo(77);
        assertThat(response.issueUrl()).isEqualTo("https://github.com/acme/api/issues/77");
        assertThat(incident.getGithubIssueNumber()).isEqualTo(77L);

        ArgumentCaptor<GitHubIssueCreateRequest> captor = ArgumentCaptor.forClass(GitHubIssueCreateRequest.class);
        verify(gitHubApiClient).createIssue(eq("acme"), eq("api"), captor.capture());
        assertThat(captor.getValue().title()).contains("OpsVision").contains("Elevated");
        assertThat(captor.getValue().body()).contains(IncidentGitHubIssueService.marker(42L));
        assertThat(captor.getValue().body()).contains("ROLLBACK");
        assertThat(captor.getValue().body()).contains("deploy regression");
        assertThat(captor.getValue().labels()).contains(IncidentGitHubIssueService.DEFAULT_LABEL);
    }

    @Test
    void createOrGetIssue_returnsExistingLocalLinkWithoutCallingGitHubCreate() {
        Incident incident = sampleIncident(5L);
        incident.setGithubIssueNumber(11L);
        incident.setGithubIssueUrl("https://github.com/acme/api/issues/11");
        incident.setGithubIssueCreatedAt(Instant.parse("2026-08-18T10:00:00Z"));
        when(incidentRepository.findByIdWithTimeline(5L)).thenReturn(Optional.of(incident));

        IncidentGitHubIssueResponse response = service.createOrGetIssue(5L);

        assertThat(response.created()).isFalse();
        assertThat(response.duplicatePrevented()).isTrue();
        assertThat(response.issueNumber()).isEqualTo(11);
        verify(gitHubApiClient, never()).createIssue(anyString(), anyString(), any());
        verify(gitHubApiClient, never()).searchIssues(anyString(), anyInt());
    }

    @Test
    void createOrGetIssue_linksRemoteDuplicateFromSearch() {
        Incident incident = sampleIncident(9L);
        when(incidentRepository.findByIdWithTimeline(9L)).thenReturn(Optional.of(incident));
        String marker = IncidentGitHubIssueService.marker(9L);
        when(gitHubApiClient.searchIssues(anyString(), anyInt())).thenReturn(new GitHubIssueSearchResponse(
                1,
                false,
                List.of(new GitHubIssueResponse(
                        1L,
                        88,
                        "existing",
                        "open",
                        "https://github.com/acme/api/issues/88",
                        "text " + marker,
                        "2026-08-18T09:00:00Z"
                ))
        ));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentGitHubIssueResponse response = service.createOrGetIssue(9L);

        assertThat(response.created()).isFalse();
        assertThat(response.duplicatePrevented()).isTrue();
        assertThat(response.issueNumber()).isEqualTo(88);
        assertThat(incident.getGithubIssueNumber()).isEqualTo(88L);
        verify(gitHubApiClient, never()).createIssue(anyString(), anyString(), any());
    }

    @Test
    void createOrGetIssue_missingIncident_throws() {
        when(incidentRepository.findByIdWithTimeline(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createOrGetIssue(404L))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void buildBody_includesMarkerAndSections() {
        Incident incident = sampleIncident(3L);
        RootCauseAnalysisResult rca = new RootCauseAnalysisResult(
                3L, Instant.now(), "m", "s",
                List.of(new ProbableCause("cause", 0.8, "METRIC_DEGRADATION", List.of("ev"))),
                List.of()
        );
        RecoveryRecommendationResult recovery = new RecoveryRecommendationResult(
                3L, Instant.now(), RecoveryAction.INVESTIGATE, "look", null, null, null,
                "METRIC_DEGRADATION", 0.8, true,
                RecoveryRecommendationResult.EXECUTION_MODE_RECOMMENDATION_ONLY,
                List.of(), List.of()
        );

        String body = IncidentGitHubIssueService.buildBody(incident, rca, recovery);

        assertThat(body).contains("## Incident summary");
        assertThat(body).contains("## Probable root cause");
        assertThat(body).contains("## Recommended action");
        assertThat(body).contains(IncidentGitHubIssueService.marker(3L));
        assertThat(body).contains("INVESTIGATE");
    }

    private static Incident sampleIncident(long id) {
        ProjectRepository repo = new ProjectRepository("acme", "api", "main", "https://github.com/acme/api");
        Deployment deployment = new Deployment(repo, "abc123", "main", "prod", DeploymentStatus.SUCCEEDED);
        setId(deployment, 7L);

        Incident incident = new Incident("Elevated error ratio (api)", IncidentSeverity.HIGH,
                Instant.parse("2026-08-18T12:00:00Z"));
        setId(incident, id);
        incident.setDeployment(deployment);
        incident.setCommitSha("abc123");
        incident.setEnvironment("prod");
        incident.setNamespace("prod");
        incident.setWorkloadName("api");
        incident.setSummary("Error rate spiked after deploy");
        return incident;
    }

    private static void setId(Object entity, long id) {
        Class<?> type = entity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("id");
                field.setAccessible(true);
                field.set(entity, id);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException("No id field on " + entity.getClass());
    }
}
