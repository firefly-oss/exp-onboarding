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

import io.fireflysoftware.experience.onboarding.core.ports.ProductFamilyView;
import io.fireflysoftware.experience.onboarding.core.ports.ReferenceCatalogPort;
import io.fireflysoftware.experience.onboarding.core.ports.Regulation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Calls domain-core-reference-data. This class does not list product families.
 */
@Component
public class HttpReferenceCatalogAdapter implements ReferenceCatalogPort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final WebClient webClient;

    @Autowired
    public HttpReferenceCatalogAdapter(
            @Value("${api-configuration.domain-platform.reference-data.base-path:http://localhost:8089}") String basePath) {
        this(WebClient.builder().baseUrl(basePath).build());
    }

    HttpReferenceCatalogAdapter(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<ProductFamilyView> findProductFamily(String code) {
        return webClient.get().uri("/api/v1/catalogs/PRODUCT_FAMILY/{code}", code)
                .retrieve()
                .bodyToMono(MAP)
                .map(body -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attributes = body.get("attributes") instanceof Map<?, ?> map
                            ? (Map<String, Object>) map : Map.of();
                    return new ProductFamilyView(
                            String.valueOf(body.get("code")),
                            Regulation.valueOf(String.valueOf(attributes.get("regulation"))),
                            attributes.get("industry") == null ? null : String.valueOf(attributes.get("industry")));
                });
    }
}
