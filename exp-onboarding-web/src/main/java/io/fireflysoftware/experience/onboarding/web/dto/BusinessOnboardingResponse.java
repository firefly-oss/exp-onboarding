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


package io.fireflysoftware.experience.onboarding.web.dto;

import io.fireflysoftware.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Web-layer response DTO for business onboarding journey status.
 * Wraps {@link BusinessOnboardingStatusDTO} from the service layer so that the
 * HTTP API contract is decoupled from the core module's internal types.
 * <p>
 * The frontend uses {@code currentPhase} and {@code nextStep} to determine which
 * screen to display and to resume after an interruption.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BusinessOnboardingResponse {

    /** Unique identifier of the onboarding workflow instance (correlationId). */
    private UUID onboardingId;

    /** Identifier of the business party registered in the domain customer service. */
    private UUID partyId;

    /** Identifier of the KYB case opened for this business. */
    private UUID kybCaseId;

    /**
     * Current phase of the onboarding journey.
     * See {@link io.fireflysoftware.experience.onboarding.core.business.workflows.BusinessOnboardingWorkflow}
     * for the full set of phase constants.
     */
    private String currentPhase;

    /** List of workflow step IDs that have been completed so far. */
    private List<String> completedSteps;

    /** ID of the next workflow step the journey is waiting for. {@code null} when completed. */
    private String nextStep;

    /** Aggregated KYB verification status derived from the workflow state. */
    private String kybStatus;

    /**
     * Maps a service-layer {@link BusinessOnboardingStatusDTO} into this web-layer response.
     *
     * @param dto the service-layer DTO to convert
     * @return a {@code BusinessOnboardingResponse} with all fields populated
     */
    public static BusinessOnboardingResponse from(BusinessOnboardingStatusDTO dto) {
        return BusinessOnboardingResponse.builder()
                .onboardingId(dto.getOnboardingId())
                .partyId(dto.getPartyId())
                .kybCaseId(dto.getKybCaseId())
                .currentPhase(dto.getCurrentPhase())
                .completedSteps(dto.getCompletedSteps())
                .nextStep(dto.getNextStep())
                .kybStatus(dto.getKybStatus())
                .build();
    }
}
