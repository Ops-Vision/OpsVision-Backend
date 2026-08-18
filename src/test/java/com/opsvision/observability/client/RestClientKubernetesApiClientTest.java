package com.opsvision.observability.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientKubernetesApiClientTest {

    private MockRestServiceServer server;
    private RestClientKubernetesApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://k8s.example");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientKubernetesApiClient(builder.build(), new ObjectMapper());
    }

    @Test
    void listDeployments_mapsRolloutAndReplicas() {
        String body = """
                {
                  "items": [{
                    "metadata": {"name": "api", "namespace": "prod"},
                    "spec": {
                      "replicas": 3,
                      "template": {"spec": {"containers": [{"image": "api:1.2.3"}]}}
                    },
                    "status": {
                      "readyReplicas": 3,
                      "availableReplicas": 3,
                      "updatedReplicas": 3,
                      "unavailableReplicas": 0,
                      "conditions": [{"type": "Available", "status": "True"}]
                    }
                  }]
                }
                """;
        server.expect(requestTo("http://k8s.example/apis/apps/v1/namespaces/prod/deployments"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<WorkloadSnapshot> result = client.listDeployments("prod", null);

        assertThat(result).hasSize(1);
        WorkloadSnapshot w = result.getFirst();
        assertThat(w.name()).isEqualTo("api");
        assertThat(w.desiredReplicas()).isEqualTo(3);
        assertThat(w.readyReplicas()).isEqualTo(3);
        assertThat(w.rolloutStatus()).isEqualTo("Available");
        assertThat(w.image()).isEqualTo("api:1.2.3");
        assertThat(w.isHealthy()).isTrue();
        server.verify();
    }

    @Test
    void listPods_sumsRestartCounts() {
        String body = """
                {
                  "items": [{
                    "metadata": {"name": "api-abc", "namespace": "prod"},
                    "status": {
                      "phase": "Running",
                      "nodeName": "node-1",
                      "startTime": "2026-08-18T10:00:00Z",
                      "containerStatuses": [
                        {"ready": true, "restartCount": 2, "state": {"running": {}}},
                        {"ready": true, "restartCount": 1, "state": {"running": {}}}
                      ]
                    }
                  }]
                }
                """;
        server.expect(requestTo("http://k8s.example/api/v1/namespaces/prod/pods?labelSelector=app%3Dapi"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<PodSnapshot> pods = client.listPods("prod", "app=api");

        assertThat(pods).hasSize(1);
        assertThat(pods.getFirst().restartCount()).isEqualTo(3);
        assertThat(pods.getFirst().ready()).isEqualTo("2/2");
        assertThat(pods.getFirst().phase()).isEqualTo("Running");
        server.verify();
    }

    @Test
    void listEvents_mapsWarnings() {
        String body = """
                {
                  "items": [{
                    "metadata": {"name": "e1", "namespace": "prod", "creationTimestamp": "2026-08-18T10:05:00Z"},
                    "type": "Warning",
                    "reason": "BackOff",
                    "message": "Back-off restarting failed container",
                    "involvedObject": {"kind": "Pod", "name": "api-abc"},
                    "count": 4,
                    "lastTimestamp": "2026-08-18T10:06:00Z"
                  }]
                }
                """;
        server.expect(requestTo("http://k8s.example/api/v1/namespaces/prod/events"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<KubernetesEventSnapshot> events = client.listEvents("prod", null);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().isWarning()).isTrue();
        assertThat(events.getFirst().reason()).isEqualTo("BackOff");
        assertThat(events.getFirst().count()).isEqualTo(4);
        server.verify();
    }
}
