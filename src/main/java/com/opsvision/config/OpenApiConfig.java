package com.opsvision.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI opsVisionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpsVision Backend API")
                        .description("""
                                Deployment Intelligence & Recovery backend.

                                Deterministic pipelines:
                                - CI evidence → confidence score → DEPLOY/REVIEW/BLOCK policy
                                - Telemetry → incident timeline → RCA → recovery recommendation → postmortem draft

                                AI explanations never override numerical scores or policy decisions.
                                Secrets are supplied via environment variables (see .env.example).
                                OpenAPI UI: /swagger-ui.html — machine-readable: /api-docs
                                """.stripIndent().trim())
                        .version("v1")
                        .contact(new Contact().name("OpsVision").email("opsvision@example.com"))
                        .license(new License().name("Proprietary")))
                .addTagsItem(new Tag().name("Deployments").description("Analyze deployments, scores, and policy"))
                .addTagsItem(new Tag().name("Incidents").description("Detection, RCA, recovery, issues, postmortems"))
                .addTagsItem(new Tag().name("Observability").description("Kubernetes and Prometheus telemetry"));
    }
}
