package com.opsvision.observability.client;

import com.opsvision.observability.model.KubernetesEventSnapshot;
import com.opsvision.observability.model.PodSnapshot;
import com.opsvision.observability.model.WorkloadSnapshot;

import java.util.List;

/**
 * Safe default when Kubernetes integration is disabled.
 */
public class NoOpKubernetesApiClient implements KubernetesApiClient {

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
    public List<WorkloadSnapshot> listDeployments(String namespace, String workloadName) {
        return List.of();
    }

    @Override
    public List<PodSnapshot> listPods(String namespace, String labelSelector) {
        return List.of();
    }

    @Override
    public List<KubernetesEventSnapshot> listEvents(String namespace, String fieldSelector) {
        return List.of();
    }
}
