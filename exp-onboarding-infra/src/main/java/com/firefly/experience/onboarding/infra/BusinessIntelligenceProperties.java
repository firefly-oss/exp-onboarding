package com.firefly.experience.onboarding.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@ConfigurationProperties(prefix = "api-configuration.domain-platform.business-intelligence")
@Data
public class BusinessIntelligenceProperties {

    private String basePath;
    private Duration timeout = Duration.ofSeconds(10);
    private Orbis orbis = new Orbis();

    @Data
    public static class Orbis {
        private UUID tenantId;
        private String enrichmentType = "company-details";
        /** BvD "Domain" header value. Identifies the target Orbis database (e.g. "Orbis"). */
        private String domain = "Orbis";
    }
}
