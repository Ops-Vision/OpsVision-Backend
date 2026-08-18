package com.opsvision.scoring.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScoringProperties.class)
public class ScoringConfig {
}
