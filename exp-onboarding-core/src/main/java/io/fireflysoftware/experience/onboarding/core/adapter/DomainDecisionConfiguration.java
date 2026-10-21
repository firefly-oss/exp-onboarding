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

import io.fireflysoftware.domain.lending.creditscoring.sdk.api.DecisionApi;
import io.fireflysoftware.domain.lending.creditscoring.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.lending.creditscoring.sdk.model.DecisionResponse;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionOutcome;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionPort;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionRequest;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionResult;
import io.fireflysoftware.experience.onboarding.core.ports.RiskDecisionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

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
        String url = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        ApiClient client = new ApiClient();
        client.setBasePath(url);
        return new SdkDecisionClient(new DecisionApi(client));
    }

    @Bean
    public DecisionPort domainDecisionPort(RiskDecisionClient riskDecisionClient) {
        return riskDecisionClient::decide;
    }

    /**
     * SDK client for {@code POST /api/v1/decisions}. Package-visible so tests can construct it.
     */
    public static final class SdkDecisionClient implements RiskDecisionClient {

        private final DecisionApi decisions;

        public SdkDecisionClient(DecisionApi decisions) {
            this.decisions = decisions;
        }

        @Override
        public Mono<DecisionResult> decide(DecisionRequest request) {
            if (request.partyId() == null || request.journeyStep() == null) {
                return Mono.error(new IllegalArgumentException("partyId and journeyStep are required"));
            }
            io.fireflysoftware.domain.lending.creditscoring.sdk.model.DecisionRequest body =
                    new io.fireflysoftware.domain.lending.creditscoring.sdk.model.DecisionRequest()
                            .partyId(request.partyId())
                            .productId(request.productId())
                            .journeyStep(io.fireflysoftware.domain.lending.creditscoring.sdk.model.DecisionRequest.JourneyStepEnum
                                    .fromValue(request.journeyStep().name()))
                            .attributes(new java.util.HashMap<>(request.attributes()));
            return decisions.evaluateDecision(body)
                    .map(view -> new DecisionResult(
                            DecisionOutcome.valueOf(outcome(view)),
                            view.getReasonCodes() == null ? java.util.List.of() : view.getReasonCodes()));
        }

        private static String outcome(DecisionResponse view) {
            if (view.getOutcome() == null || view.getOutcome().getValue() == null) {
                throw new IllegalStateException("DOMAIN_RESPONSE_UNREADABLE");
            }
            return view.getOutcome().getValue();
        }
    }
}
