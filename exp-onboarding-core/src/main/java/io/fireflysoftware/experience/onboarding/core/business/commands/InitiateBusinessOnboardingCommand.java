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


package io.fireflysoftware.experience.onboarding.core.business.commands;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Command DTO for initiating a business onboarding journey.
 * Used as the initial input payload when starting the workflow.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InitiateBusinessOnboardingCommand {
    @NotBlank(message = "Business name is required")
    private String businessName;
    @NotBlank(message = "Registration number is required")
    private String registrationNumber;
    @NotBlank(message = "Country of incorporation is required")
    private String countryOfIncorporation;
    private String contactEmail;
    private String contactPhone;
    private UUID tenantId;
    /** Party sent to POST /api/v1/decisions. Falls back to the tenant id when omitted. */
    private UUID partyId;
    /** Actor checked by AccessPort. Falls back to the party id when omitted. */
    private UUID actorId;
    /**
     * PRODUCT_FAMILY code. When set, regulation comes from ReferenceCatalogPort.
     * When omitted, the existing regulated party workflow runs.
     */
    private String productFamily;
}
