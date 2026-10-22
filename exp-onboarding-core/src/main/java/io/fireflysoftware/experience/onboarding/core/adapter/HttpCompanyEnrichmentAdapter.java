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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.fireflysoftware.experience.onboarding.core.ports.CompanyEnrichmentPort;
import io.fireflysoftware.experience.onboarding.infra.BusinessIntelligenceProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Posts {@value #PATH} to
 * {@code api-configuration.domain-platform.business-intelligence.base-path}.
 * Selected when {@code firefly.onboarding.enrichment.adapter=live}.
 * An installer replaces this class when the enrichment protocol changes.
 * This bean is not created while that property is unset.
 */
@Component
@ConditionalOnProperty(name = "firefly.onboarding.enrichment.adapter", havingValue = "live")
public final class HttpCompanyEnrichmentAdapter implements CompanyEnrichmentPort {

    public static final String PATH = "/api/v1/enrichment/smart";

    private static final Logger log = LoggerFactory.getLogger(HttpCompanyEnrichmentAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebClient client;
    private final BusinessIntelligenceProperties properties;
    private final String endpoint;

    @Autowired
    public HttpCompanyEnrichmentAdapter(BusinessIntelligenceProperties properties) {
        this(webClient(properties), properties, trim(requireBase(properties)));
    }

    HttpCompanyEnrichmentAdapter(WebClient client, BusinessIntelligenceProperties properties, String endpoint) {
        this.client = client;
        this.properties = properties;
        this.endpoint = endpoint;
    }

    String configuredEndpoint() {
        return endpoint;
    }

    @Override
    public Mono<JsonNode> companyData(String query) {
        if (query == null || query.isBlank()) {
            return Mono.error(new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "bvdId is required"));
        }
        if (properties.getOrbis() == null || properties.getOrbis().getTenantId() == null) {
            return Mono.error(new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_MISSING",
                    "orbis enricher tenantId is not configured"));
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("bvdId", query);
        if (properties.getOrbis().getDomain() != null && !properties.getOrbis().getDomain().isBlank()) {
            params.put("domain", properties.getOrbis().getDomain());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", properties.getOrbis().getEnrichmentType());
        body.put("tenantId", properties.getOrbis().getTenantId().toString());
        body.put("strategy", "RAW");
        body.put("params", params);

        return client.post()
                .uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(response -> extractCompanyData(JSON.valueToTree(response)))
                .onErrorMap(WebClientResponseException.class, HttpCompanyEnrichmentAdapter::mapUpstreamError);
    }

    private static Mono<JsonNode> extractCompanyData(JsonNode envelope) {
        if (envelope == null || envelope.isNull() || envelope instanceof NullNode) {
            return Mono.error(new BusinessException(HttpStatus.BAD_GATEWAY, "UPSTREAM_PROTOCOL_ERROR",
                    "enrichment response was empty"));
        }
        if (!envelope.path("success").asBoolean(false)) {
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

    private static BusinessException mapUpstreamError(WebClientResponseException ex) {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        if (ex.getStatusCode().is4xxClientError()) {
            status = HttpStatus.valueOf(ex.getStatusCode().value());
        }
        log.warn("Business-intelligence enrichment failed status={}", ex.getStatusCode());
        return new BusinessException(status, "UPSTREAM_BUSINESS_INTELLIGENCE_ERROR",
                "business-intelligence call failed: " + ex.getStatusCode());
    }

    private static WebClient webClient(BusinessIntelligenceProperties properties) {
        String base = trim(requireBase(properties));
        Duration timeout = properties.getTimeout() == null ? Duration.ofSeconds(10) : properties.getTimeout();
        long timeoutMs = timeout.toMillis();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMs)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));
        return WebClient.builder()
                .baseUrl(base)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private static String requireBase(BusinessIntelligenceProperties properties) {
        if (properties == null || properties.getBasePath() == null || properties.getBasePath().isBlank()) {
            throw new IllegalStateException(
                    "api-configuration.domain-platform.business-intelligence.base-path is not set");
        }
        return properties.getBasePath();
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
