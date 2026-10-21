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

import io.fireflysoftware.experience.onboarding.core.ports.AccessDecision;
import io.fireflysoftware.experience.onboarding.core.ports.AccessPort;
import io.fireflysoftware.experience.onboarding.core.ports.AccessRequest;
import io.fireflysoftware.experience.onboarding.core.ports.IdentityPort;
import io.fireflysoftware.experience.onboarding.core.ports.SubjectRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Binds identity and access to domain-core-security-center over HTTP.
 * Experience code does not import Keycloak. The security center chooses
 * {@code firefly.security-center.identity.adapter} (sandbox, keycloak, cognito, or internal-db).
 */
@Configuration
@ConditionalOnProperty(name = "firefly.experience.access.adapter", havingValue = "domain", matchIfMissing = true)
public class DomainAccessConfiguration {

    @Bean
    public WebClient securityCenterWebClient(
            @Value("${api-configuration.domain-platform.security-center.base-path:http://localhost:8085}")
            String basePath) {
        return WebClient.builder().baseUrl(basePath).build();
    }

    @Bean
    public IdentityPort identityPort(WebClient securityCenterWebClient) {
        return new HttpIdentityClient(securityCenterWebClient);
    }

    @Bean
    public AccessPort accessPort(WebClient securityCenterWebClient) {
        return new HttpAccessClient(securityCenterWebClient);
    }

    public static final class HttpIdentityClient implements IdentityPort {

        private final WebClient webClient;

        public HttpIdentityClient(WebClient webClient) {
            this.webClient = webClient;
        }

        @Override
        public Mono<SubjectRef> resolve(String username) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("username", username);
            return webClient.post()
                    .uri("/api/v1/identity/subjects")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(IdentityPayload.class)
                    .map(payload -> new SubjectRef(UUID.fromString(payload.actorId()), payload.provider()));
        }

        private record IdentityPayload(String actorId, String provider) {
        }
    }

    public static final class HttpAccessClient implements AccessPort {

        private final WebClient webClient;

        public HttpAccessClient(WebClient webClient) {
            this.webClient = webClient;
        }

        @Override
        public Mono<AccessDecision> check(AccessRequest request) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("actorId", request.actorId().toString());
            body.put("action", request.action().name());
            body.put("resourceType", request.resourceType());
            body.put("resourceId", request.resourceId().toString());
            return webClient.post()
                    .uri("/api/v1/access/checks")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AccessPayload.class)
                    .map(payload -> Boolean.TRUE.equals(payload.allowed())
                            ? AccessDecision.permit(payload.reasonCode() == null ? "SANDBOX_GRANT" : payload.reasonCode())
                            : AccessDecision.denied(payload.reasonCode() == null ? "ACTOR_NOT_ENTITLED" : payload.reasonCode()));
        }

        private record AccessPayload(Boolean allowed, String reasonCode) {
        }
    }
}
