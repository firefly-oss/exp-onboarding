package com.firefly.experience.onboarding.core.business.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
