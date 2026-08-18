package com.opsvision.observability.service;

import com.opsvision.observability.client.KubernetesApiClient;
import com.opsvision.observability.client.PrometheusClient;
import com.opsvision.observability.config.ObservabilityProperties;
import com.opsvision.observability.exception.ObservabilityException;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.TelemetrySnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryCollectionServiceTest {

    @Mock
    private KubernetesApiClient kubernetesApiClient;

    @Mock
    private PrometheusClient prometheusClient;

    private ObservabilityProperties properties;
    private TelemetryCollectionService service;

    @BeforeEach
    void setUp() {
        properties = new ObservabilityProperties();
        properties.setEnabled(true);
        properties.getKubernetes().setDefaultNamespace("prod");
        properties.getPrometheus().setServiceSelector("job=\"api\"");
        properties.getPrometheus().setRequestRateQuery("vector(10)");
        properties.getPrometheus().setErrorRateQuery("vector(1)");
        properties.getPrometheus().setErrorRatioQuery("vector(0.1)");
        properties.getPrometheus().setLatencyP95Query("vector(0.2)");
        properties.getPrometheus().setCpuQuery("vector(0.5)");
        properties.getPrometheus().setMemoryQuery("vector(1048576)");
        properties.getPrometheus().setAvailabilityQuery("vector(1)");
        service = new TelemetryCollectionService(kubernetesApiClient, prometheusClient, properties);
    }

    @Test
    void collect_happyPath_mergesK8sAndPrometheus() {
        when(kubernetesApiClient.isAvailable()).thenReturn(true);
        when(prometheusClient.isAvailable()).thenReturn(true);
        when(kubernetesApiClient.listDeployments("prod", "api")).thenReturn(List.of(
                new WorkloadSnapshot("api", "prod", "Deployment", 2, 2, 2, 2, 0, "Available", "api:1")
        ));
        when(kubernetesApiClient.listPods(eq("prod"), eq("app=api"))).thenReturn(List.of(
                new PodSnapshot("api-1", "prod", "Running", "1/1", 2, null, "n1", Instant.parse("2026-08-18T10:00:00Z"))
        ));
        when(kubernetesApiClient.listEvents(eq("prod"), anyString())).thenReturn(List.of(
                new KubernetesEventSnapshot("e1", "prod", "Warning", "Unhealthy", "probe failed",
                        "Pod", "api-1", 1, Instant.parse("2026-08-18T10:01:00Z"))
        ));
        when(prometheusClient.queryScalar(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0);
            if (q.contains("vector(10)")) {
                return Optional.of(10.0);
            }
            if (q.contains("vector(1)") && q.equals("vector(1)")) {
                return Optional.of(1.0);
            }
            if (q.contains("0.1")) {
                return Optional.of(0.1);
            }
            if (q.contains("0.2")) {
                return Optional.of(0.2);
            }
            if (q.contains("0.5")) {
                return Optional.of(0.5);
            }
            if (q.contains("1048576")) {
                return Optional.of(1_048_576.0);
            }
            return Optional.of(1.0);
        });

        TelemetrySnapshot snap = service.collect("prod", "api");

        assertThat(snap.kubernetesAvailable()).isTrue();
        assertThat(snap.prometheusAvailable()).isTrue();
        assertThat(snap.workloads()).hasSize(1);
        assertThat(snap.pods()).hasSize(1);
        assertThat(snap.totalPodRestarts()).isEqualTo(2);
        assertThat(snap.warningEvents()).hasSize(1);
        assertThat(snap.metrics().requestRatePerSecond()).isEqualTo(10.0);
        assertThat(snap.metrics().errorRatio()).isEqualTo(0.1);
        assertThat(snap.metrics().hasData()).isTrue();
    }

    @Test
    void collect_whenDisabled_skipsClients() {
        properties.setEnabled(false);

        TelemetrySnapshot snap = service.collect();

        assertThat(snap.kubernetesAvailable()).isFalse();
        assertThat(snap.prometheusAvailable()).isFalse();
        assertThat(snap.notes()).anyMatch(n -> n.contains("disabled"));
        verify(kubernetesApiClient, never()).listDeployments(any(), any());
        verify(prometheusClient, never()).queryScalar(any());
    }

    @Test
    void collect_kubernetesFailure_isSoftAndDoesNotThrow() {
        when(kubernetesApiClient.isAvailable()).thenReturn(true);
        when(prometheusClient.isAvailable()).thenReturn(false);
        when(kubernetesApiClient.listDeployments(anyString(), any())).thenThrow(
                new ObservabilityException("k8s down")
        );

        TelemetrySnapshot snap = service.collect("prod", "api");

        assertThat(snap.kubernetesAvailable()).isFalse();
        assertThat(snap.notes()).anyMatch(n -> n.contains("Kubernetes collection failed"));
    }

    @Test
    void collect_prometheusFailure_isSoft() {
        when(kubernetesApiClient.isAvailable()).thenReturn(false);
        when(prometheusClient.isAvailable()).thenReturn(true);
        when(prometheusClient.queryScalar(anyString())).thenThrow(new ObservabilityException("prom down"));

        TelemetrySnapshot snap = service.collect("prod", null);

        assertThat(snap.prometheusAvailable()).isFalse();
        assertThat(snap.notes()).anyMatch(n -> n.contains("Prometheus collection failed"));
    }
}
