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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fireflysoftware.experience.onboarding.core.ports.CompanyEnrichmentPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Deterministic company profile. {@link #SAMPLE_QUERY} returns the same profile on every call.
 * Any other query completes empty. This adapter does not open a socket.
 * An installer selects {@link HttpCompanyEnrichmentAdapter} with
 * {@code firefly.onboarding.enrichment.adapter=live}.
 */
@Component
@ConditionalOnProperty(name = "firefly.onboarding.enrichment.adapter", havingValue = "sandbox", matchIfMissing = true)
public final class SandboxCompanyEnrichmentAdapter implements CompanyEnrichmentPort {

    public static final String SAMPLE_QUERY = "sample-company";

    private static final JsonNode PROFILE = profile();

    @Override
    public Mono<JsonNode> companyData(String query) {
        if (!SAMPLE_QUERY.equals(query)) {
            return Mono.empty();
        }
        return Mono.just(PROFILE.deepCopy());
    }

    private static JsonNode profile() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("NAME", "Sandbox Sample Company");
        node.put("BVD_ID_NUMBER", SAMPLE_QUERY);
        node.put("CITY", "Austin");
        node.put("COUNTRY", "US");
        node.put("VAT_NUMBER", "US-SAMPLE-0001");
        return node;
    }
}
