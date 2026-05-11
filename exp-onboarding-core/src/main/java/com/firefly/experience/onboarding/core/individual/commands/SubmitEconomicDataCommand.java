package com.firefly.experience.onboarding.core.individual.commands;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Command DTO for submitting economic / employment data during the individual
 * onboarding journey. Sent as signal payload to advance the workflow past the
 * {@code economic-data-submitted} gate.
 *
 * <p>Validation strategy: structural constraints (enum membership, max size,
 * non-negative numbers) are enforced here. Cross-field conditional requirements
 * (e.g. {@code employmentType} is required when {@code employmentStatus} is
 * private/public/civil) are validated in the service so the error message can be
 * specific and reference the related field.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitEconomicDataCommand {

    @NotBlank(message = "employmentStatus is required")
    @Pattern(
            regexp = "^(private|public|civil|selfEmployed|entrepreneur|unemployedBenefit|unemployed|retired|other)$",
            message = "employmentStatus must be one of: private, public, civil, selfEmployed, entrepreneur, "
                    + "unemployedBenefit, unemployed, retired, other"
    )
    private String employmentStatus;

    @Pattern(
            regexp = "^(permanent|temporary|project|internship|na)$",
            message = "employmentType must be one of: permanent, temporary, project, internship, na"
    )
    private String employmentType;

    @Size(max = 200, message = "employer must not exceed 200 characters")
    private String employer;

    @Size(max = 200, message = "position must not exceed 200 characters")
    private String position;

    private LocalDate employmentStartDate;

    /** Paydays per year — only 12 or 14 are accepted; service rejects any other value. */
    @Min(value = 12, message = "annualPaydays must be 12 or 14")
    @Max(value = 14, message = "annualPaydays must be 12 or 14")
    private Integer annualPaydays;

    @DecimalMin(value = "0.0", inclusive = true, message = "monthlySalary must be non-negative")
    private BigDecimal monthlySalary;

    @NotBlank(message = "housingType is required")
    @Pattern(
            regexp = "^(rent|mortgage|owned|family)$",
            message = "housingType must be one of: rent, mortgage, owned, family"
    )
    private String housingType;

    @NotNull(message = "housingCost is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "housingCost must be non-negative")
    private BigDecimal housingCost;

    private LocalDate housingStartDate;

    /** Number of currently active loans: 0..3 (3 means three or more). */
    @Min(value = 0, message = "existingLoans must be between 0 and 3")
    @Max(value = 3, message = "existingLoans must be between 0 and 3")
    private Integer existingLoans;

    @DecimalMin(value = "0.0", inclusive = true, message = "otherDebts must be non-negative")
    private BigDecimal otherDebts;
}
