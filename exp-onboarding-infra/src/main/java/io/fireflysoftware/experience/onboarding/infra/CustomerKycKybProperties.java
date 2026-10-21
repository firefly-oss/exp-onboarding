/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.experience.onboarding.infra;

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
