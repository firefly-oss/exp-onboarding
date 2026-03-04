package com.firefly.experience.onboarding.core.individual.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Command DTO for initiating an individual onboarding journey.
 * Used as the initial input payload when starting the workflow.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InitiateOnboardingCommand {
    @NotBlank(message = "First name is required")
    private String firstName;
    @NotBlank(message = "Last name is required")
    private String lastName;
    @NotBlank(message = "Email is required")
    private String email;
    private String phone;
    private String nationalIdNumber;
    @Builder.Default
    private String dueDiligenceLevel = "STANDARD";
}
