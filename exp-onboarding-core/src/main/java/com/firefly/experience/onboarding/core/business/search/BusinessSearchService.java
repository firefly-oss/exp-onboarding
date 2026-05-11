package com.firefly.experience.onboarding.core.business.search;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

/**
 * Business search BFF service. Exposes two operations:
 * <ol>
 *   <li>{@link #searchMatches(BusinessSearchRequest)} — list candidate companies from
 *       the third-party provider that match the supplied criteria (CIF, name, …).</li>
 *   <li>{@link #getCompanyData(String)} — fetch the {@code companyData} section
 *       of the composite enrichment response for the chosen candidate, identified
 *       by its provider-specific id (BvDId).</li>
 * </ol>
 */
public interface BusinessSearchService {

    /** Returns the raw provider match list (array of provider-shaped JSON objects). */
    Mono<JsonNode> searchMatches(BusinessSearchRequest request);

    /** Returns only the {@code companyData} subtree of the enrichment response. */
    Mono<JsonNode> getCompanyData(String bvdId);
}
