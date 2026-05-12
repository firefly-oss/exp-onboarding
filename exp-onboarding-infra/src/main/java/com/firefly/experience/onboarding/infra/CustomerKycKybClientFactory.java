package com.firefly.experience.onboarding.infra;

import com.firefly.domain.kyc.kyb.sdk.api.KybApi;
import com.firefly.domain.kyc.kyb.sdk.api.KycApi;
import com.firefly.domain.kyc.kyb.sdk.invoker.ApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Factory that creates and configures the Customer KYC/KYB SDK {@link ApiClient}
 * and exposes domain API beans for dependency injection.
 */
@Component
public class CustomerKycKybClientFactory {

    private static final int MAX_IN_MEMORY_SIZE = 20 * 1024 * 1024;

    private final ApiClient apiClient;

    /**
     * Initialises the API client with the base path from configuration properties.
     *
     * @param properties connection properties for the Customer KYC/KYB service
     */
    public CustomerKycKybClientFactory(CustomerKycKybProperties properties) {
        WebClient webClient = ApiClient.buildWebClientBuilder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
        this.apiClient = new ApiClient(webClient);
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

    /**
     * Provides the {@link KybApi} bean for KYB verification (businesses).
     *
     * @return a ready-to-use KybApi instance
     */
    @Bean
    public KybApi kybApi() {
        return new KybApi(apiClient);
    }
}
