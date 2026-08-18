package com.opsvision.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kubernetes and Prometheus collection settings. Secrets bind from environment only.
 */
@ConfigurationProperties(prefix = "opsvision.observability")
public class ObservabilityProperties {

    private boolean enabled = false;

    private final Kubernetes kubernetes = new Kubernetes();
    private final Prometheus prometheus = new Prometheus();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Kubernetes getKubernetes() {
        return kubernetes;
    }

    public Prometheus getPrometheus() {
        return prometheus;
    }

    public static class Kubernetes {
        /**
         * When false, Kubernetes client is a no-op even if master switch is on.
         */
        private boolean enabled = true;

        /**
         * API server base URL, e.g. https://kubernetes.default.svc
         */
        private String apiBaseUrl = "https://kubernetes.default.svc";

        /**
         * Bearer token (service account). Prefer OPSVISION_K8S_TOKEN env.
         */
        private String token = "";

        private String defaultNamespace = "default";

        private String defaultWorkload = "";

        private int connectTimeoutMs = 5_000;

        private int readTimeoutMs = 15_000;

        /**
         * When true, skip TLS certificate verification (local/dev only).
         */
        private boolean insecureSkipTlsVerify = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getDefaultNamespace() {
            return defaultNamespace;
        }

        public void setDefaultNamespace(String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
        }

        public String getDefaultWorkload() {
            return defaultWorkload;
        }

        public void setDefaultWorkload(String defaultWorkload) {
            this.defaultWorkload = defaultWorkload;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public boolean isInsecureSkipTlsVerify() {
            return insecureSkipTlsVerify;
        }

        public void setInsecureSkipTlsVerify(boolean insecureSkipTlsVerify) {
            this.insecureSkipTlsVerify = insecureSkipTlsVerify;
        }

        public boolean hasToken() {
            return token != null && !token.isBlank();
        }
    }

    public static class Prometheus {
        private boolean enabled = true;

        /**
         * Prometheus HTTP API base, e.g. http://prometheus:9090
         */
        private String baseUrl = "http://localhost:9090";

        private int connectTimeoutMs = 5_000;

        private int readTimeoutMs = 15_000;

        /**
         * Label matcher fragment for service metrics, e.g. job="my-app" or app="opsvision"
         */
        private String serviceSelector = "";

        private String requestRateQuery = "";
        private String errorRateQuery = "";
        private String errorRatioQuery = "";
        private String latencyP50Query = "";
        private String latencyP95Query = "";
        private String latencyP99Query = "";
        private String cpuQuery = "";
        private String memoryQuery = "";
        private String availabilityQuery = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public String getServiceSelector() {
            return serviceSelector;
        }

        public void setServiceSelector(String serviceSelector) {
            this.serviceSelector = serviceSelector;
        }

        public String getRequestRateQuery() {
            return requestRateQuery;
        }

        public void setRequestRateQuery(String requestRateQuery) {
            this.requestRateQuery = requestRateQuery;
        }

        public String getErrorRateQuery() {
            return errorRateQuery;
        }

        public void setErrorRateQuery(String errorRateQuery) {
            this.errorRateQuery = errorRateQuery;
        }

        public String getErrorRatioQuery() {
            return errorRatioQuery;
        }

        public void setErrorRatioQuery(String errorRatioQuery) {
            this.errorRatioQuery = errorRatioQuery;
        }

        public String getLatencyP50Query() {
            return latencyP50Query;
        }

        public void setLatencyP50Query(String latencyP50Query) {
            this.latencyP50Query = latencyP50Query;
        }

        public String getLatencyP95Query() {
            return latencyP95Query;
        }

        public void setLatencyP95Query(String latencyP95Query) {
            this.latencyP95Query = latencyP95Query;
        }

        public String getLatencyP99Query() {
            return latencyP99Query;
        }

        public void setLatencyP99Query(String latencyP99Query) {
            this.latencyP99Query = latencyP99Query;
        }

        public String getCpuQuery() {
            return cpuQuery;
        }

        public void setCpuQuery(String cpuQuery) {
            this.cpuQuery = cpuQuery;
        }

        public String getMemoryQuery() {
            return memoryQuery;
        }

        public void setMemoryQuery(String memoryQuery) {
            this.memoryQuery = memoryQuery;
        }

        public String getAvailabilityQuery() {
            return availabilityQuery;
        }

        public void setAvailabilityQuery(String availabilityQuery) {
            this.availabilityQuery = availabilityQuery;
        }
    }
}
