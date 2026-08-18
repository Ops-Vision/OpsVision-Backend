package com.opsvision.observability.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsvision.observability.model.MetricSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientPrometheusClientTest {

    private MockRestServiceServer server;
    private RestClientPrometheusClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prom.example");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientPrometheusClient(builder.build(), new ObjectMapper());
    }

    @Test
    void query_parsesVectorResult() {
        String body = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [{
                      "metric": {"__name__": "up", "job": "api"},
                      "value": [1690000000, "1"]
                    }]
                  }
                }
                """;
        server.expect(requestTo("http://prom.example/api/v1/query?query=up"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<MetricSample> samples = client.query("up");

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().value()).isEqualTo(1.0d);
        assertThat(samples.getFirst().labels()).containsEntry("job", "api");
        server.verify();
    }

    @Test
    void queryScalar_returnsFirstValue() {
        String body = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [{
                      "metric": {},
                      "value": [1690000000, "0.18"]
                    }]
                  }
                }
                """;
        server.expect(requestTo("http://prom.example/api/v1/query?query=error_ratio"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Optional<Double> value = client.queryScalar("error_ratio");

        assertThat(value).contains(0.18d);
        server.verify();
    }

    @Test
    void query_blankReturnsEmpty() {
        assertThat(client.query("  ")).isEmpty();
    }
}
