package com.firefly.experience.onboarding.core.individual.queries;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Data transfer object representing the current KYC verification status for an onboarding journey.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KycStatusDTO {

    /** Unique identifier of the KYC case. */
    private UUID caseId;

    /** Current verification status (e.g. NOT_STARTED, CASE_OPENED, DOCUMENTS_SUBMITTED, PENDING, APPROVED). */
    private String status;
}
