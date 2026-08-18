package com.opsvision.policy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PolicyProperties.class)
public class PolicyConfig {
}
