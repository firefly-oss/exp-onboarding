package com.firefly.experience.onboarding.core.individual.commands;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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

    /**
     * Marital status. Accepted values (case-insensitive): SINGLE, MARRIED, SEPARATED, DIVORCED, WIDOWED.
     * Front-end may send lowercase; the handler normalises to uppercase before mapping to the SDK enum.
     */
    @Pattern(
            regexp = "^(?i)(SINGLE|MARRIED|SEPARATED|DIVORCED|WIDOWED)$",
            message = "maritalStatus must be one of: SINGLE, MARRIED, SEPARATED, DIVORCED, WIDOWED"
    )
    private String maritalStatus;

    /**
     * Number of children declared by the applicant. Must be between 0 and 20 inclusive.
     */
    @Min(value = 0, message = "numberOfChildren must be greater than or equal to 0")
    @Max(value = 20, message = "numberOfChildren must be less than or equal to 20")
    private Integer numberOfChildren;
}
