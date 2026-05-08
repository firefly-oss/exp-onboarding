package com.firefly.experience.onboarding.core.business.commands;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
    @Valid
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

        /**
         * Signer email address.
         */
        @Email(message = "email must be a valid email address")
        private String email;

        /**
         * Whether this signer is authorized to sign on behalf of the legal entity.
         */
        private Boolean signingAuthorized;

        /**
         * Whether this signer is a Politically Exposed Person.
         */
        private Boolean isPep;
    }
}
