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

import io.fireflysoftware.domain.people.sdk.api.BusinessesApi;
import io.fireflysoftware.domain.people.sdk.api.CustomersApi;
import io.fireflysoftware.domain.people.sdk.invoker.ApiClient;
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

    @Bean
    public io.fireflysoftware.domain.people.sdk.api.IndustryClassificationsApi industryClassificationsApi() {
        return new io.fireflysoftware.domain.people.sdk.api.IndustryClassificationsApi(apiClient);
    }

    @Bean
    public io.fireflysoftware.domain.people.sdk.api.PartyAttributesApi partyAttributesApi() {
        return new io.fireflysoftware.domain.people.sdk.api.PartyAttributesApi(apiClient);
    }
}
