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


package io.fireflysoftware.experience.onboarding.core.ports;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

/**
 * Company profile read for onboarding. The default adapter is
 * {@link io.fireflysoftware.experience.onboarding.core.adapter.SandboxCompanyEnrichmentAdapter}.
 * An installer selects
 * {@link io.fireflysoftware.experience.onboarding.core.adapter.HttpCompanyEnrichmentAdapter}
 * with {@code firefly.onboarding.enrichment.adapter=live}.
 */
public interface CompanyEnrichmentPort {

    /**
     * Company profile for {@code query}. Completes empty when the provider has no profile.
     */
    Mono<JsonNode> companyData(String query);
}
