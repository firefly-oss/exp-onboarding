package com.firefly.experience.onboarding.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for the Customer KYC/KYB domain-tier API.
 * <p>
 * Binds to {@code api-configuration.domain-platform.customer-kyc-kyb} in application.yaml.
 */
@Component
@ConfigurationProperties(prefix = "api-configuration.domain-platform.customer-kyc-kyb")
@Data
public class CustomerKycKybProperties {

    /** Base URL of the Customer KYC/KYB service (e.g. {@code http://localhost:8083}). */
    private String basePath;

    /** Read/connect timeout for SDK calls. Defaults to 10 seconds (KYB is slower due to external verification). */
    private Duration timeout = Duration.ofSeconds(10);
}
