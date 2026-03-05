package com.firefly.experience.onboarding.core.business.queries;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing the current state of a business onboarding journey.
 * Reconstructed from the workflow's internal execution state via @WorkflowQuery.
 * The frontend uses {@code currentPhase} and {@code nextStep} to determine
 * which screen to show and resume from after interruption.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BusinessOnboardingStatusDTO {
    private UUID onboardingId;
    private UUID partyId;
    private UUID kybCaseId;
    private String currentPhase;
    private List<String> completedSteps;
    private String nextStep;
    private String kybStatus;
}
