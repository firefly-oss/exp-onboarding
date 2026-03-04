package com.firefly.experience.onboarding.infra;

import com.firefly.domain.kyc.kyb.sdk.api.KycApi;
import com.firefly.domain.kyc.kyb.sdk.invoker.ApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Factory that creates and configures the Customer KYC/KYB SDK {@link ApiClient}
 * and exposes domain API beans for dependency injection.
 */
@Component
public class CustomerKycKybClientFactory {

    private final ApiClient apiClient;

    /**
     * Initialises the API client with the base path from configuration properties.
     *
     * @param properties connection properties for the Customer KYC/KYB service
     */
    public CustomerKycKybClientFactory(CustomerKycKybProperties properties) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(properties.getBasePath());
    }

    /**
     * Provides the {@link KycApi} bean for KYC verification (individuals).
     *
     * @return a ready-to-use KycApi instance
     */
    @Bean
    public KycApi kycApi() {
        return new KycApi(apiClient);
    }
}
