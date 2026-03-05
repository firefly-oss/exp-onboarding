package com.firefly.experience.onboarding.infra;

import com.firefly.domain.people.sdk.api.BusinessesApi;
import com.firefly.domain.people.sdk.api.CustomersApi;
import com.firefly.domain.people.sdk.invoker.ApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Factory that creates and configures the Customer People SDK {@link ApiClient}
 * and exposes domain API beans for dependency injection.
 */
@Component
public class CustomerPeopleClientFactory {

    private final ApiClient apiClient;

    /**
     * Initialises the API client with the base path from configuration properties.
     *
     * @param properties connection properties for the Customer People service
     */
    public CustomerPeopleClientFactory(CustomerPeopleProperties properties) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(properties.getBasePath());
    }

    /**
     * Provides the {@link CustomersApi} bean for individual customer operations.
     *
     * @return a ready-to-use CustomersApi instance
     */
    @Bean
    public CustomersApi customersApi() {
        return new CustomersApi(apiClient);
    }

    /**
     * Provides the {@link BusinessesApi} bean for business customer operations.
     *
     * @return a ready-to-use BusinessesApi instance
     */
    @Bean
    public BusinessesApi businessesApi() {
        return new BusinessesApi(apiClient);
    }
}
