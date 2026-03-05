package com.firefly.experience.onboarding.core.business.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Command DTO for submitting authorized signers during business onboarding.
 * Advances the journey past the authorized-signers-submitted signal gate.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitAuthorizedSignersCommand {
    private List<SignerEntry> signers;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SignerEntry {
        private String firstName;
        private String lastName;
        private String documentType;
        private String documentNumber;
        private String role;
        private String powerDocumentReference;
    }
}
