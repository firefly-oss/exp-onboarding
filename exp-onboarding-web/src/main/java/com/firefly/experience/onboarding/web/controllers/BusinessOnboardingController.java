package com.firefly.experience.onboarding.web.controllers;

import com.firefly.experience.onboarding.core.business.commands.InitiateBusinessOnboardingCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCorporateDocumentsCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitUbosCommand;
import com.firefly.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
import com.firefly.experience.onboarding.core.business.queries.KybStatusDTO;
import com.firefly.experience.onboarding.core.business.services.BusinessOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the business onboarding journey (Persona Jurídica).
 * Each endpoint is atomic: it either starts the workflow or sends a signal to advance it.
 */
@RestController
@RequestMapping("/api/v1/onboarding/businesses")
@RequiredArgsConstructor
@Tag(name = "Business Onboarding",
     description = "Atomic endpoints for the business onboarding journey")
public class BusinessOnboardingController {

    private static final String KEY_ONBOARDING_ID = "onboardingId";
    private static final String KEY_STATUS = "status";

    private static final String STATUS_COMPANY_DATA_SUBMITTED = "COMPANY_DATA_SUBMITTED";
    private static final String STATUS_UBOS_SUBMITTED = "UBOS_SUBMITTED";
    private static final String STATUS_CORPORATE_DOCUMENTS_SUBMITTED = "CORPORATE_DOCUMENTS_SUBMITTED";
    private static final String STATUS_AUTHORIZED_SIGNERS_SUBMITTED = "AUTHORIZED_SIGNERS_SUBMITTED";
    private static final String STATUS_KYB_TRIGGERED = "KYB_TRIGGERED";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final BusinessOnboardingService onboardingService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Initiate Business Onboarding",
               description = "Start a new business onboarding journey — registers the business party, "
                   + "opens a KYB case, and sends a welcome notification")
    public Mono<ResponseEntity<BusinessOnboardingStatusDTO>> initiateOnboarding(
            @Valid @RequestBody InitiateBusinessOnboardingCommand command) {
        return onboardingService.initiateOnboarding(command)
                .map(status -> ResponseEntity.status(HttpStatus.CREATED).body(status));
    }

    @GetMapping(value = "/{onboardingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Onboarding Status",
               description = "Retrieve the current state of a business onboarding process. "
                   + "Returns completed steps, current phase, and next expected action.")
    public Mono<ResponseEntity<BusinessOnboardingStatusDTO>> getOnboardingStatus(
            @PathVariable UUID onboardingId) {
        return onboardingService.getStatus(onboardingId)
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/{onboardingId}/company-data",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Company Data",
               description = "Submit company details (legal name, tax ID, address, business activity). "
                   + "Advances the journey past the company-data gate.")
    public Mono<ResponseEntity<Map<String, Object>>> submitCompanyData(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitCompanyDataCommand command) {
        return onboardingService.submitCompanyData(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_COMPANY_DATA_SUBMITTED)));
    }

    @PostMapping(value = "/{onboardingId}/ubos",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Ultimate Beneficial Owners",
               description = "Submit UBO declarations with ownership percentages and PEP status. "
                   + "Advances the journey past the UBOs gate.")
    public Mono<ResponseEntity<Map<String, Object>>> submitUbos(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitUbosCommand command) {
        return onboardingService.submitUbos(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_UBOS_SUBMITTED)));
    }

    @PostMapping(value = "/{onboardingId}/corporate-documents",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Corporate Documents",
               description = "Submit corporate documentation (articles of incorporation, board resolution, "
                   + "proof of address, tax certificate). Advances the journey past the documents gate.")
    public Mono<ResponseEntity<Map<String, Object>>> submitCorporateDocuments(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitCorporateDocumentsCommand command) {
        return onboardingService.submitCorporateDocuments(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_CORPORATE_DOCUMENTS_SUBMITTED)));
    }

    @PostMapping(value = "/{onboardingId}/authorized-signers",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Authorized Signers",
               description = "Submit authorized signers for the business (legal representatives, "
                   + "power of attorney holders). Advances the journey past the signers gate.")
    public Mono<ResponseEntity<Map<String, Object>>> submitAuthorizedSigners(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitAuthorizedSignersCommand command) {
        return onboardingService.submitAuthorizedSigners(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_AUTHORIZED_SIGNERS_SUBMITTED)));
    }

    @PostMapping(value = "/{onboardingId}/kyb", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Trigger KYB Verification",
               description = "Trigger Know Your Business verification after all documents and data "
                   + "are submitted. Advances the journey past the KYB gate.")
    public Mono<ResponseEntity<Map<String, Object>>> triggerKybVerification(
            @PathVariable UUID onboardingId) {
        return onboardingService.triggerKybVerification(onboardingId)
                .thenReturn(ResponseEntity.accepted().body(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_KYB_TRIGGERED)));
    }

    @GetMapping(value = "/{onboardingId}/kyb/status",
                produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get KYB Status",
               description = "Retrieve the KYB verification status for the business onboarding journey")
    public Mono<ResponseEntity<KybStatusDTO>> getKybStatus(
            @PathVariable UUID onboardingId) {
        return onboardingService.getKybStatus(onboardingId)
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/{onboardingId}/completion",
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Complete Onboarding",
               description = "Complete the business onboarding process — verifies KYB approval, "
                   + "activates the business party, and sends a completion notification")
    public Mono<ResponseEntity<Map<String, Object>>> completeOnboarding(
            @PathVariable UUID onboardingId) {
        return onboardingService.completeOnboarding(onboardingId)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_COMPLETED)));
    }
}
