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

import io.fireflysoftware.experience.onboarding.core.ports.ContractingPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls domain-common-contracts create. Quote, bind, and claim are not implemented here.
 */
@Component
public class HttpContractingAdapter implements ContractingPort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final WebClient webClient;

    @Autowired
    public HttpContractingAdapter(
            @Value("${api-configuration.domain-platform.common-contracts.base-path:http://localhost:8090}") String basePath) {
        this(WebClient.builder().baseUrl(basePath).build());
    }

    HttpContractingAdapter(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<UUID> open(UUID partyId, String contractType, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partyId", partyId);
        body.put("contractType", contractType);
        body.put("description", description);
        return webClient.post().uri("/api/v1/contracts").bodyValue(body)
                .retrieve()
                .bodyToMono(MAP)
                .map(bodyMap -> UUID.fromString(String.valueOf(bodyMap.get("contractId"))));
    }
}
