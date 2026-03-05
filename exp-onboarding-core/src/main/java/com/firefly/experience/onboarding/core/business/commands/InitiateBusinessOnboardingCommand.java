package com.firefly.experience.onboarding.core.business.commands;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String tenantId;
}
