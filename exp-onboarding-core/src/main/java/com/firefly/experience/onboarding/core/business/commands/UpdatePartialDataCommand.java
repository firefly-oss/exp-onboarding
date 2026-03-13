package com.firefly.experience.onboarding.core.business.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command DTO for applying partial corrections to an in-progress business onboarding journey.
 * All fields are optional; only non-null values are applied.
 * This command does NOT advance the workflow state — it is used for out-of-band corrections
 * to contact info or the registered business name before the KYB trigger is sent.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePartialDataCommand {

    /** Updated legal business name (if corrected after initiation). */
    private String businessName;

    /** Updated contact e-mail for the business representative. */
    private String contactEmail;

    /** Updated contact phone number for the business representative. */
    private String contactPhone;
}
