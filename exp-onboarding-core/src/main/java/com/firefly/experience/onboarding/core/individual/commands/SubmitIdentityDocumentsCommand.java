package com.firefly.experience.onboarding.core.individual.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Command DTO for submitting identity documents during the onboarding journey.
 * Sent as signal payload to advance the workflow past the identity-documents gate.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitIdentityDocumentsCommand {
    @NotBlank
    private String documentType;
    @NotBlank
    private String documentNumber;
    private String documentContent;
    private String mimeType;
}
