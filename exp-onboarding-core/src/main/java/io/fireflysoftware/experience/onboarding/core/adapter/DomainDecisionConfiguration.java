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


package io.fireflysoftware.experience.onboarding.core.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionOutcome;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionPort;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionRequest;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionResult;
import io.fireflysoftware.experience.onboarding.core.ports.RiskDecisionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls domain-lending-credit-scoring {@code POST /api/v1/decisions}.
 * The rules engine stays in that service. This class does not import it.
 * <p>
 * Active unless {@code firefly.experience.decision.adapter=sandbox}.
 */
@Configuration
@ConditionalOnProperty(name = "firefly.experience.decision.adapter", havingValue = "domain", matchIfMissing = true)
public class DomainDecisionConfiguration {

    @Bean
    public RiskDecisionClient riskDecisionClient(
            @Value("${api-configuration.domain-platform.lending-credit-scoring.base-path:http://localhost:8042}")
            String basePath) {
        return new HttpDecisionClient(WebClient.builder().baseUrl(basePath).build());
    }

    @Bean
    public DecisionPort domainDecisionPort(RiskDecisionClient riskDecisionClient) {
        return riskDecisionClient::decide;
    }

    /**
     * HTTP client for {@code POST /api/v1/decisions}. Package-visible so tests can construct it.
     */
    public static final class HttpDecisionClient implements RiskDecisionClient {

        private final WebClient webClient;

        public HttpDecisionClient(WebClient webClient) {
            this.webClient = webClient;
        }

        @Override
        public Mono<DecisionResult> decide(DecisionRequest request) {
            if (request.partyId() == null || request.journeyStep() == null || request.journeyStep().isBlank()) {
                return Mono.error(new IllegalArgumentException("partyId and journeyStep are required"));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("partyId", request.partyId());
            if (request.productId() != null) {
                body.put("productId", request.productId());
            }
            body.put("journeyStep", request.journeyStep());
            body.put("attributes", request.attributes());
            return webClient.post()
                    .uri("/api/v1/decisions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(DecisionPayload.class)
                    .map(payload -> new DecisionResult(
                            DecisionOutcome.valueOf(payload.outcome()),
                            payload.reasonCodes() == null ? List.of() : payload.reasonCodes()));
        }

        private record DecisionPayload(String outcome, java.util.List<String> reasonCodes) {
        }
    }
}
