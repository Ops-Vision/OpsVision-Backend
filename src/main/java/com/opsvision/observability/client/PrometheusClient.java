package com.opsvision.observability.client;

import com.opsvision.observability.model.MetricSample;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over Prometheus HTTP API (instant queries).
 */
public interface PrometheusClient {

    String name();

    boolean isAvailable();

    /**
     * Run an instant query and return all result samples.
     */
    List<MetricSample> query(String promQl);

    /**
     * Convenience: first scalar/vector value if present.
     */
    Optional<Double> queryScalar(String promQl);
}
