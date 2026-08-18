package com.opsvision.observability.client;

import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;

import java.util.List;

/**
 * Abstraction over the Kubernetes API for post-deployment telemetry collection.
 */
public interface KubernetesApiClient {

    /**
     * Logical client name (e.g. rest, none).
     */
    String name();

    boolean isAvailable();

    List<WorkloadSnapshot> listDeployments(String namespace, String workloadName);

    List<PodSnapshot> listPods(String namespace, String labelSelector);

    List<KubernetesEventSnapshot> listEvents(String namespace, String fieldSelector);
}
