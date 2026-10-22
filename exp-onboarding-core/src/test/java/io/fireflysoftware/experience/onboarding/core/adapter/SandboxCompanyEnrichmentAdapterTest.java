/*
 * Copyright 2026 Firefly Software Foundation
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


package io.fireflysoftware.experience.onboarding.core.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import io.fireflysoftware.experience.onboarding.core.business.search.impl.BusinessSearchServiceImpl;
import io.fireflysoftware.experience.onboarding.infra.BusinessIntelligenceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxCompanyEnrichmentAdapterTest {

    @Test
    void sandboxReturnsTheStableProfileForTheKnownSample() {
        var adapter = new SandboxCompanyEnrichmentAdapter();
        JsonNode first = adapter.companyData(SandboxCompanyEnrichmentAdapter.SAMPLE_QUERY).block(Duration.ofSeconds(2));
        JsonNode second = adapter.companyData(SandboxCompanyEnrichmentAdapter.SAMPLE_QUERY).block(Duration.ofSeconds(2));

        assertThat(first).isEqualTo(second);
        assertThat(first.path("NAME").asText()).isEqualTo("Sandbox Sample Company");
        assertThat(first.path("BVD_ID_NUMBER").asText()).isEqualTo(SandboxCompanyEnrichmentAdapter.SAMPLE_QUERY);
        assertThat(first.path("CITY").asText()).isEqualTo("Austin");
        assertThat(first.path("COUNTRY").asText()).isEqualTo("US");

        var service = new BusinessSearchServiceImpl(adapter, new BusinessIntelligenceProperties());
        JsonNode throughOnboarding = service.getCompanyData(SandboxCompanyEnrichmentAdapter.SAMPLE_QUERY)
                .block(Duration.ofSeconds(2));
        assertThat(throughOnboarding).isEqualTo(first);
    }

    @Test
    void sandboxReturnsEmptyForAnyOtherQuery() {
        var adapter = new SandboxCompanyEnrichmentAdapter();
        StepVerifier.create(adapter.companyData("other-company"))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
        StepVerifier.create(adapter.companyData(null))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
        StepVerifier.create(new BusinessSearchServiceImpl(adapter, new BusinessIntelligenceProperties())
                        .getCompanyData("BE0440734148"))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void liveAdapterPostsTheConfiguredEndpoint() {
        var properties = new BusinessIntelligenceProperties();
        properties.setBasePath("http://enrichment.internal/");
        properties.getOrbis().setTenantId(UUID.fromString("efdd318a-d07a-45a6-bfbf-a9922f696c6f"));
        var configured = new HttpCompanyEnrichmentAdapter(properties);
        assertThat(configured.configuredEndpoint()).isEqualTo("http://enrichment.internal");

        AtomicReference<String> seenUrl = new AtomicReference<>();
        WebClient client = WebClient.builder()
                .baseUrl("http://enrichment.internal")
                .exchangeFunction(request -> {
                    seenUrl.set(request.url().toString());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"success\":true,\"enrichedData\":{\"companyData\":{\"NAME\":\"Live Co\"}}}")
                            .build());
                })
                .build();
        JsonNode company = new HttpCompanyEnrichmentAdapter(client, properties, "http://enrichment.internal")
                .companyData("BE1")
                .block(Duration.ofSeconds(2));

        assertThat(seenUrl.get()).isEqualTo("http://enrichment.internal/api/v1/enrichment/smart");
        assertThat(company.path("NAME").asText()).isEqualTo("Live Co");
        assertThatThrownBy(() -> new HttpCompanyEnrichmentAdapter(new BusinessIntelligenceProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base-path");
    }
}
