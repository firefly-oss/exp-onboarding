package com.firefly.experience.onboarding.core.business.queries;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO representing the KYB verification status for a business onboarding journey.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KybStatusDTO {
    private UUID caseId;
    private String status;
    private String rejectionReason;
}
