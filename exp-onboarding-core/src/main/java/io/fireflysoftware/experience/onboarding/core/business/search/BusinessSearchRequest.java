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


package io.fireflysoftware.experience.onboarding.core.business.search;

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
