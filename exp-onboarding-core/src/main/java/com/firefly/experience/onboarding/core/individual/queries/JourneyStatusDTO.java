package com.firefly.experience.onboarding.core.individual.queries;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing the current state of an individual onboarding journey.
 * Reconstructed from the workflow's internal execution state via @WorkflowQuery.
 * The frontend uses {@code currentPhase} and {@code nextStep} to determine
 * which screen to show and resume from after interruption.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JourneyStatusDTO {
    private UUID partyId;
    private UUID kycCaseId;
    private String currentPhase;
    private List<String> completedSteps;
    private String nextStep;
    private String kycVerificationStatus;
}
