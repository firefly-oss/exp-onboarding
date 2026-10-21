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


package io.fireflysoftware.experience.onboarding.core.business.search.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.fireflysoftware.data.business.intelligence.sdk.api.BusinessIntelligenceApi;
import io.fireflysoftware.data.business.intelligence.sdk.invoker.ApiClient;
import io.fireflysoftware.data.business.intelligence.sdk.model.SelectCompanyRequest;
import io.fireflysoftware.experience.onboarding.core.business.search.BusinessSearchRequest;
import io.fireflysoftware.experience.onboarding.core.business.search.BusinessSearchService;
import io.fireflysoftware.experience.onboarding.infra.BusinessIntelligenceClientFactory;
import io.fireflysoftware.experience.onboarding.infra.BusinessIntelligenceProperties;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class BusinessSearchServiceImpl implements BusinessSearchService {

    private static final String ENRICHMENT_SMART_PATH = "/api/v1/enrichment/smart";

    private final WebClient client;
    private final BusinessIntelligenceApi companies;
    private final BusinessIntelligenceProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public BusinessSearchServiceImpl(
            @Qualifier(BusinessIntelligenceClientFactory.BEAN_NAME) WebClient client,
            BusinessIntelligenceProperties properties) {
        this.client = client;
        ApiClient apiClient = new ApiClient();
        String basePath = properties.getBasePath();
        if (basePath != null && !basePath.isBlank()) {
            apiClient.setBasePath(basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath);
        }
        this.companies = new BusinessIntelligenceApi(apiClient);
        this.properties = properties;
    }

    @Override
    public Mono<JsonNode> searchMatches(BusinessSearchRequest request) {
        if (request == null
                || (isBlank(request.getNationalId()) && isBlank(request.getName()))) {
            return Mono.error(new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "either nationalId or name is required to search businesses"));
        }
        log.debug("Searching businesses nationalId={} name={} country={}",
                request.getNationalId(), request.getName(), request.getCountry());

        if (properties.getBasePath() == null || properties.getBasePath().isBlank()) {
            return Mono.error(new IllegalStateException("PROPERTY_NOT_SET"));
        }
        return companies.selectCompany(new SelectCompanyRequest()
                        .name(request.getName())
                        .city(request.getCity())
                        .country(request.getCountry())
                        .address(request.getAddress())
                        .nationalId(request.getNationalId())
                        .postCode(request.getPostCode()))
                .collectList()
                .map(rows -> json.<JsonNode>valueToTree(rows))
                .onErrorMap(WebClientResponseException.class, this::mapUpstreamError);
    }

    @Override
    public Mono<JsonNode> getCompanyData(String bvdId) {
        if (isBlank(bvdId)) {
            return Mono.error(new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "bvdId is required"));
        }
        if (properties.getOrbis() == null || properties.getOrbis().getTenantId() == null) {
            return Mono.error(new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_MISSING",
                    "orbis enricher tenantId is not configured"));
        }
        log.debug("Fetching company data bvdId={}", bvdId);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("bvdId", bvdId);
        if (properties.getOrbis().getDomain() != null && !properties.getOrbis().getDomain().isBlank()) {
            // Forwarded as the upstream Orbis "Domain" HTTP header (required by the provider).
            params.put("domain", properties.getOrbis().getDomain());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", properties.getOrbis().getEnrichmentType());
        body.put("tenantId", properties.getOrbis().getTenantId().toString());
        body.put("strategy", "RAW");
        body.put("params", params);

        return client.post()
                .uri(ENRICHMENT_SMART_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(this::extractCompanyData)
                .onErrorMap(WebClientResponseException.class, this::mapUpstreamError);
    }

    private Mono<JsonNode> extractCompanyData(JsonNode envelope) {
        // EnrichmentApiResponse → { success, enrichedData: OrbisCompositeResponse, ... }
        // With strategy=RAW, enrichedData carries the unmapped provider response.
        if (envelope == null || envelope.isNull() || envelope instanceof NullNode) {
            return Mono.error(new BusinessException(HttpStatus.BAD_GATEWAY, "UPSTREAM_PROTOCOL_ERROR",
                    "enrichment response was empty"));
        }
        boolean success = envelope.path("success").asBoolean(false);
        if (!success) {
            String error = envelope.path("error").asText("upstream enrichment failed");
            return Mono.error(new BusinessException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ENRICHMENT_FAILED", error));
        }
        JsonNode companyData = envelope.path("enrichedData").path("companyData");
        if (companyData.isMissingNode() || companyData.isNull()) {
            return Mono.error(new BusinessException(HttpStatus.BAD_GATEWAY, "UPSTREAM_PROTOCOL_ERROR",
                    "enrichment response did not include companyData"));
        }
        return Mono.just(companyData);
    }

    private BusinessException mapUpstreamError(WebClientResponseException ex) {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        if (ex.getStatusCode().is4xxClientError()) {
            status = HttpStatus.valueOf(ex.getStatusCode().value());
        }
        log.warn("Business-intelligence upstream call failed status={} body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
        return new BusinessException(status, "UPSTREAM_BUSINESS_INTELLIGENCE_ERROR",
                "business-intelligence call failed: " + ex.getStatusCode());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
