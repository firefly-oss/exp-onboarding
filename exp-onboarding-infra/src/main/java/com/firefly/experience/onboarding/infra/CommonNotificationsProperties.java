package com.firefly.experience.onboarding.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for the Common Notifications domain-tier API.
 * <p>
 * Binds to {@code api-configuration.domain-platform.common-notifications} in application.yaml.
 */
@Component
@ConfigurationProperties(prefix = "api-configuration.domain-platform.common-notifications")
@Data
public class CommonNotificationsProperties {

    /** Base URL of the Common Notifications service (e.g. {@code http://localhost:8095}). */
    private String basePath;

    /** Read/connect timeout for SDK calls. Defaults to 5 seconds. */
    private Duration timeout = Duration.ofSeconds(5);
}
