package com.firefly.experience.onboarding.core.business.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for the business search endpoint. Mirrors the upstream
 * {@code SelectCompanyRequest} fields verbatim so callers can supply any
 * combination of identification or location attributes.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Criteria for business search against the third-party business intelligence provider")
public class BusinessSearchRequest {

    @Schema(description = "Company name or trade name", example = "ACME Iberica SA")
    private String name;

    @Schema(description = "City of incorporation or operation", example = "Madrid")
    private String city;

    @Schema(description = "ISO country name or code", example = "Spain")
    private String country;

    @Schema(description = "Postal address", example = "Calle Mayor 1")
    private String address;

    @Schema(description = "National business identifier (CIF/VAT/etc.)", example = "B12345678")
    private String nationalId;

    @Schema(description = "Postal code", example = "28013")
    private String postCode;
}
