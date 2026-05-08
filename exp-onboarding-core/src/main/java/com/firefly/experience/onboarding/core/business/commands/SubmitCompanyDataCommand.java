package com.firefly.experience.onboarding.core.business.commands;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Command DTO for submitting company data during business onboarding.
 * Advances the journey past the company-data signal gate.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitCompanyDataCommand {
    private String legalName;
    private String tradeName;
    private LocalDate incorporationDate;
    private String businessType;
    private String taxId;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String postalCode;
    private String country;
    private String businessActivity;

    /**
     * Employee headcount range. Front-end value; mapped to the domain SDK 'employeeRange' field.
     * Accepted bands: 1-5, 6-25, 26-50, 51-250, 250+.
     */
    @Pattern(
            regexp = "^(1-5|6-25|26-50|51-250|250\\+)$",
            message = "numberOfEmployees must be one of: 1-5, 6-25, 26-50, 51-250, 250+"
    )
    private String numberOfEmployees;

    /**
     * Declared annual revenue. Must be zero or positive.
     */
    @PositiveOrZero(message = "annualRevenue must be zero or positive")
    private BigDecimal annualRevenue;

    /**
     * CNAE economic activity code (Spanish national classification). Up to 10 characters.
     */
    @Size(max = 10, message = "cnaeCode must be at most 10 characters")
    private String cnaeCode;

    /**
     * Primary business contact full name.
     */
    private String contactName;

    /**
     * Primary business contact role/position within the company.
     */
    private String contactPosition;

    /**
     * Primary business contact email address.
     */
    @Email(message = "contactEmail must be a valid email address")
    private String contactEmail;

    /**
     * Primary business contact phone number.
     */
    @Size(max = 50, message = "contactPhone must be at most 50 characters")
    private String contactPhone;
}
