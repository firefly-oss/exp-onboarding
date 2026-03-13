package com.firefly.experience.onboarding.web.dto;

import com.firefly.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
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
     * See {@link com.firefly.experience.onboarding.core.business.workflows.BusinessOnboardingWorkflow}
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
