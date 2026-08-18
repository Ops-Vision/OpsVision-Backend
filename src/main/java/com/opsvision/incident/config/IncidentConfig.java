package com.opsvision.incident.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IncidentProperties.class)
public class IncidentConfig {
}
