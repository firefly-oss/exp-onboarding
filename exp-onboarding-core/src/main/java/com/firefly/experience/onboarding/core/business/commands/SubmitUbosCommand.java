package com.firefly.experience.onboarding.core.business.commands;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Command DTO for submitting Ultimate Beneficial Owners (UBOs) during business onboarding.
 * Advances the journey past the ubos-submitted signal gate.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitUbosCommand {
    @Valid
    private List<UboEntry> ubos;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UboEntry {
        private String firstName;
        private String lastName;
        private String documentType;
        private String documentNumber;
        private BigDecimal ownershipPercentage;
        private boolean pep;

        /**
         * UBO email address.
         */
        @Email(message = "email must be a valid email address")
        private String email;

        /**
         * Type of ownership: DIRECT or INDIRECT (case-insensitive).
         */
        @Pattern(
                regexp = "^(?i)(DIRECT|INDIRECT)$",
                message = "ownershipType must be either DIRECT or INDIRECT"
        )
        private String ownershipType;
    }
}
