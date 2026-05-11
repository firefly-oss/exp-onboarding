package com.firefly.experience.onboarding.web.controllers;

import com.firefly.experience.onboarding.core.individual.commands.InitiateOnboardingCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitEconomicDataCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import com.firefly.experience.onboarding.core.individual.queries.JourneyStatusDTO;
import com.firefly.experience.onboarding.core.individual.queries.KycStatusDTO;
import com.firefly.experience.onboarding.core.individual.services.IndividualOnboardingService;
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
 * REST controller for the individual customer onboarding journey.
 * Each endpoint is atomic: it either starts the workflow or sends a signal to advance it.
 */
@RestController
@RequestMapping("/api/v1/onboarding/individuals")
@RequiredArgsConstructor
@Tag(name = "Individual Onboarding",
     description = "Atomic endpoints for the individual customer onboarding journey")
public class IndividualOnboardingController {

    private static final String KEY_ONBOARDING_ID = "onboardingId";
    private static final String KEY_STATUS = "status";

    private static final String STATUS_PERSONAL_DATA_SUBMITTED = "PERSONAL_DATA_SUBMITTED";
    private static final String STATUS_ECONOMIC_DATA_SUBMITTED = "ECONOMIC_DATA_SUBMITTED";
    private static final String STATUS_DOCUMENTS_SUBMITTED = "DOCUMENTS_SUBMITTED";
    private static final String STATUS_KYC_TRIGGERED = "KYC_TRIGGERED";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final IndividualOnboardingService onboardingService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Initiate Onboarding",
               description = "Start a new individual onboarding journey — registers the party, "
                   + "opens a KYC case, and sends a welcome notification")
    public Mono<ResponseEntity<JourneyStatusDTO>> initiateOnboarding(
            @Valid @RequestBody InitiateOnboardingCommand command) {
        return onboardingService.initiateOnboarding(command)
                .map(status -> ResponseEntity.status(HttpStatus.CREATED).body(status));
    }

    @GetMapping(value = "/{onboardingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Onboarding Status",
               description = "Retrieve the current state of an individual onboarding process. "
                   + "Returns completed steps, current phase, and next expected action.")
    public Mono<ResponseEntity<JourneyStatusDTO>> getOnboardingStatus(
            @PathVariable UUID onboardingId) {
        return onboardingService.getJourneyStatus(onboardingId)
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/{onboardingId}/personal-data",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Personal Data",
               description = "Submit address and personal details for the onboarding individual. "
                   + "Advances the journey past the personal-data gate.")
    public Mono<ResponseEntity<Map<String, Object>>> submitPersonalData(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitPersonalDataCommand command) {
        return onboardingService.submitPersonalData(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_PERSONAL_DATA_SUBMITTED)));
    }

    @PostMapping(value = "/{onboardingId}/economic-data",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Economic Data",
               description = "Submit employment, housing and debt data for the onboarding individual. "
                   + "Advances the journey past the economic-data gate.")
    public Mono<ResponseEntity<Map<String, Object>>> submitEconomicData(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitEconomicDataCommand command) {
        return onboardingService.submitEconomicData(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_ECONOMIC_DATA_SUBMITTED)));
    }

    @PostMapping(value = "/{onboardingId}/identity-documents",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Identity Documents",
               description = "Upload identity documents (passport, national ID, etc.) for KYC evidence. "
                   + "Advances the journey past the identity-documents gate.")
    public Mono<ResponseEntity<Map<String, Object>>> submitIdentityDocuments(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitIdentityDocumentsCommand command) {
        return onboardingService.submitIdentityDocuments(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_DOCUMENTS_SUBMITTED)));
    }

    @PostMapping(value = "/{onboardingId}/kyc", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Trigger KYC Verification",
               description = "Trigger KYC verification for the onboarding individual after "
                   + "documents are submitted. Advances the journey past the KYC gate.")
    public Mono<ResponseEntity<Map<String, Object>>> triggerKyc(
            @PathVariable UUID onboardingId) {
        return onboardingService.triggerKyc(onboardingId)
                .thenReturn(ResponseEntity.accepted().body(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_KYC_TRIGGERED)));
    }

    @GetMapping(value = "/{onboardingId}/kyc/status",
                produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get KYC Status",
               description = "Retrieve the KYC verification status for the onboarding individual")
    public Mono<ResponseEntity<KycStatusDTO>> getKycStatus(
            @PathVariable UUID onboardingId) {
        return onboardingService.getKycStatus(onboardingId)
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/{onboardingId}/completion",
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Complete Onboarding",
               description = "Complete the onboarding process — verifies KYC approval, "
                   + "activates the party, and sends a completion notification")
    public Mono<ResponseEntity<Map<String, Object>>> completeOnboarding(
            @PathVariable UUID onboardingId) {
        return onboardingService.completeOnboarding(onboardingId)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_COMPLETED)));
    }
}
