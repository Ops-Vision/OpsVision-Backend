package com.opsvision.observability.client;

import com.opsvision.observability.model.MetricSample;

import java.util.List;
import java.util.Optional;

/**
 * Safe default when Prometheus integration is disabled.
 */
public class NoOpPrometheusClient implements PrometheusClient {

    public static final String NAME = "none";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<MetricSample> query(String promQl) {
        return List.of();
    }

    @Override
    public Optional<Double> queryScalar(String promQl) {
        return Optional.empty();
    }
}
