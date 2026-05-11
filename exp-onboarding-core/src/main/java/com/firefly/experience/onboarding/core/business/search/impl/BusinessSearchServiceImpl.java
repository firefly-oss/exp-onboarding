package com.firefly.experience.onboarding.core.business.search.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.firefly.experience.onboarding.core.business.search.BusinessSearchRequest;
import com.firefly.experience.onboarding.core.business.search.BusinessSearchService;
import com.firefly.experience.onboarding.infra.BusinessIntelligenceClientFactory;
import com.firefly.experience.onboarding.infra.BusinessIntelligenceProperties;
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

    private static final String SELECT_COMPANY_PATH = "/api/v1/business-intelligence/select-company";
    private static final String ENRICHMENT_SMART_PATH = "/api/v1/enrichment/smart";

    private final WebClient client;
    private final BusinessIntelligenceProperties properties;

    public BusinessSearchServiceImpl(
            @Qualifier(BusinessIntelligenceClientFactory.BEAN_NAME) WebClient client,
            BusinessIntelligenceProperties properties) {
        this.client = client;
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

        return client.post()
                .uri(SELECT_COMPANY_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
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
