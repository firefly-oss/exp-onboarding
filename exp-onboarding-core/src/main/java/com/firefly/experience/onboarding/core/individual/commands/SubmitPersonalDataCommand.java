package com.firefly.experience.onboarding.core.individual.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command DTO for submitting personal data during the onboarding journey.
 * Sent as signal payload to advance the workflow past the personal-data gate.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitPersonalDataCommand {
    private String dateOfBirth;
    private String nationality;
    private String countryOfResidence;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String postalCode;
    private String country;
    private String taxIdNumber;
}
