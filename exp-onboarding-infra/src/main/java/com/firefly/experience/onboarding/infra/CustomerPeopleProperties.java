package com.firefly.experience.onboarding.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for the Customer People domain-tier API.
 * <p>
 * Binds to {@code api-configuration.domain-platform.customer-people} in application.yaml.
 */
@Component
@ConfigurationProperties(prefix = "api-configuration.domain-platform.customer-people")
@Data
public class CustomerPeopleProperties {

    /** Base URL of the Customer People service (e.g. {@code http://localhost:8081}). */
    private String basePath;

    /** Read/connect timeout for SDK calls. Defaults to 5 seconds. */
    private Duration timeout = Duration.ofSeconds(5);
}
