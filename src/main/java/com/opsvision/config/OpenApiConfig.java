package com.opsvision.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI opsVisionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpsVision Backend API")
                        .description("Deployment Intelligence & Recovery — analysis, scoring, and policy")
                        .version("v1"));
    }
}
