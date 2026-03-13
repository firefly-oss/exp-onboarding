package com.firefly.experience.onboarding.web.controllers;

import com.firefly.experience.onboarding.core.business.commands.InitiateBusinessOnboardingCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCorporateDocumentsCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitUbosCommand;
import com.firefly.experience.onboarding.core.business.commands.UpdatePartialDataCommand;
import com.firefly.experience.onboarding.core.business.services.BusinessOnboardingService;
import com.firefly.experience.onboarding.web.dto.BusinessOnboardingResponse;
import com.firefly.experience.onboarding.web.dto.KybStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 *
 * <p>Each endpoint is <strong>atomic</strong>: it either starts the long-running workflow
 * or sends a signal to advance it past the next {@code @WaitForSignal} gate. The client
 * decides when to call each endpoint based on the {@code currentPhase} and {@code nextStep}
 * fields returned by the status endpoint.
 *
 * <p>Recovery pattern: if the client loses connection mid-journey, it calls
 * {@code GET /{id}} to retrieve the current phase and resumes from where it left off.
 *
 * <p>Base path: {@code /api/v1/onboarding/businesses}
 */
@RestController
@RequestMapping("/api/v1/onboarding/businesses")
@RequiredArgsConstructor
@Tag(name = "Business Onboarding",
     description = "Atomic endpoints for the business onboarding journey (Persona Jurídica). "
         + "Each POST/PATCH endpoint either starts the workflow or advances it via a signal gate.")
public class BusinessOnboardingController {

    private static final String KEY_ONBOARDING_ID = "onboardingId";
    private static final String KEY_STATUS = "status";

    private static final String STATUS_PARTIAL_DATA_UPDATED = "PARTIAL_DATA_UPDATED";
    private static final String STATUS_COMPANY_DATA_SUBMITTED = "COMPANY_DATA_SUBMITTED";
    private static final String STATUS_UBOS_SUBMITTED = "UBOS_SUBMITTED";
    private static final String STATUS_CORPORATE_DOCUMENTS_SUBMITTED = "CORPORATE_DOCUMENTS_SUBMITTED";
    private static final String STATUS_AUTHORIZED_SIGNERS_SUBMITTED = "AUTHORIZED_SIGNERS_SUBMITTED";
    private static final String STATUS_KYB_TRIGGERED = "KYB_TRIGGERED";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final BusinessOnboardingService onboardingService;

    /**
     * Initiates a new business onboarding journey.
     *
     * <p>Starts the signal-driven workflow, which synchronously executes the first layer
     * of steps (register party, open KYB case, send welcome notification) before blocking
     * at the first signal gate. Returns the initial journey status.
     *
     * @param command the initiation payload with business name, registration number and country
     * @return 201 Created with the initial {@link BusinessOnboardingResponse}
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Initiate Business Onboarding",
               description = "Start a new business onboarding journey — registers the business party, "
                   + "opens a KYB case, and sends a welcome notification. "
                   + "Blocks until the first signal gate and returns the initial status.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Onboarding journey started successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = BusinessOnboardingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload",
            content = @Content),
        @ApiResponse(responseCode = "409", description = "Duplicate onboarding already in progress for this business",
            content = @Content)
    })
    public Mono<ResponseEntity<BusinessOnboardingResponse>> initiateOnboarding(
            @Valid @RequestBody InitiateBusinessOnboardingCommand command) {
        return onboardingService.initiateOnboarding(command)
                .map(BusinessOnboardingResponse::from)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    /**
     * Retrieves the complete status of a business onboarding process.
     *
     * <p>Reconstructs the journey state from the workflow's persisted execution context.
     * The frontend uses {@code currentPhase} and {@code nextStep} to determine which
     * screen to show and how to resume after an interruption.
     *
     * @param onboardingId the unique identifier of the onboarding workflow instance
     * @return 200 OK with the current {@link BusinessOnboardingResponse}
     */
    @GetMapping(value = "/{onboardingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Onboarding Status",
               description = "Retrieve the current state of a business onboarding process. "
                   + "Returns completed steps, current phase, and next expected action. "
                   + "Use this to resume an interrupted journey.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Journey status retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = BusinessOnboardingResponse.class))),
        @ApiResponse(responseCode = "404", description = "Onboarding journey not found",
            content = @Content)
    })
    public Mono<ResponseEntity<BusinessOnboardingResponse>> getOnboardingStatus(
            @PathVariable UUID onboardingId) {
        return onboardingService.getStatus(onboardingId)
                .map(BusinessOnboardingResponse::from)
                .map(ResponseEntity::ok);
    }

    /**
     * Applies partial corrections to an in-progress onboarding journey.
     *
     * <p>This endpoint does <strong>not</strong> advance the workflow state — it is used
     * to correct contact info or the business name after initiation, without re-triggering
     * any signal gate. Only non-null fields in the request body are applied.
     *
     * @param onboardingId the unique identifier of the onboarding workflow instance
     * @param command      the partial update payload; all fields are optional
     * @return 200 OK with a status acknowledgement map
     */
    @PatchMapping(value = "/{onboardingId}",
                  consumes = MediaType.APPLICATION_JSON_VALUE,
                  produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update Partial Data",
               description = "Apply partial corrections to an in-progress business onboarding journey "
                   + "(e.g. contact e-mail, phone, or business name). "
                   + "Does NOT advance the workflow — use the dedicated step endpoints for that.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Partial data updated successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "400", description = "Invalid request payload",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "Onboarding journey not found",
            content = @Content)
    })
    public Mono<ResponseEntity<Map<String, Object>>> updatePartialData(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody UpdatePartialDataCommand command) {
        return onboardingService.updatePartialData(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_PARTIAL_DATA_UPDATED)));
    }

    /**
     * Submits company data for the onboarding journey.
     *
     * <p>Sends a {@code company-data-submitted} signal to the long-running workflow,
     * advancing it past the company-data gate. The workflow updates the registered legal
     * entity and business address in the domain service.
     *
     * @param onboardingId the unique identifier of the onboarding workflow instance
     * @param command      the company details (legal name, tax ID, address, business activity)
     * @return 200 OK with a status acknowledgement map
     */
    @PostMapping(value = "/{onboardingId}/company-data",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Company Data",
               description = "Submit company details (legal name, tax ID, address, business activity). "
                   + "Sends a signal to advance the journey past the company-data gate.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Company data accepted and workflow advanced",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "400", description = "Invalid company data payload",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "Onboarding journey not found",
            content = @Content),
        @ApiResponse(responseCode = "409", description = "Journey is not at the company-data gate",
            content = @Content)
    })
    public Mono<ResponseEntity<Map<String, Object>>> submitCompanyData(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitCompanyDataCommand command) {
        return onboardingService.submitCompanyData(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_COMPANY_DATA_SUBMITTED)));
    }

    /**
     * Submits Ultimate Beneficial Owner (UBO) declarations for the onboarding journey.
     *
     * <p>Sends a {@code ubos-submitted} signal to the workflow, advancing it past the UBOs gate.
     * The workflow registers the UBOs against the KYB case in the domain KYB service.
     *
     * @param onboardingId the unique identifier of the onboarding workflow instance
     * @param command      the list of UBO entries with ownership percentages and PEP flags
     * @return 200 OK with a status acknowledgement map
     */
    @PostMapping(value = "/{onboardingId}/ubos",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Ultimate Beneficial Owners",
               description = "Submit UBO declarations with ownership percentages and PEP status. "
                   + "Sends a signal to advance the journey past the UBOs gate.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "UBOs accepted and workflow advanced",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "400", description = "Invalid UBO payload",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "Onboarding journey not found",
            content = @Content),
        @ApiResponse(responseCode = "409", description = "Journey is not at the UBOs gate",
            content = @Content)
    })
    public Mono<ResponseEntity<Map<String, Object>>> submitUbos(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitUbosCommand command) {
        return onboardingService.submitUbos(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_UBOS_SUBMITTED)));
    }

    /**
     * Submits corporate documentation for the onboarding journey.
     *
     * <p>Sends a {@code corporate-documents-submitted} signal to the workflow, advancing it
     * past the corporate-documents gate. The workflow submits the document references to the
     * KYB service.
     *
     * @param onboardingId the unique identifier of the onboarding workflow instance
     * @param command      the list of corporate document entries (type + reference)
     * @return 200 OK with a status acknowledgement map
     */
    @PostMapping(value = "/{onboardingId}/corporate-documents",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Corporate Documents",
               description = "Submit corporate documentation (articles of incorporation, board resolution, "
                   + "proof of address, tax certificate, commercial registry extract). "
                   + "Sends a signal to advance the journey past the documents gate.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Corporate documents accepted and workflow advanced",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "400", description = "Invalid documents payload",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "Onboarding journey not found",
            content = @Content),
        @ApiResponse(responseCode = "409", description = "Journey is not at the corporate-documents gate",
            content = @Content)
    })
    public Mono<ResponseEntity<Map<String, Object>>> submitCorporateDocuments(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitCorporateDocumentsCommand command) {
        return onboardingService.submitCorporateDocuments(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_CORPORATE_DOCUMENTS_SUBMITTED)));
    }

    /**
     * Submits authorized signers for the onboarding journey.
     *
     * <p>Sends an {@code authorized-signers-submitted} signal to the workflow, advancing it
     * past the authorized-signers gate. The workflow registers the signers' document references
     * in the KYB service.
     *
     * @param onboardingId the unique identifier of the onboarding workflow instance
     * @param command      the list of authorized signer entries (name, role, power document reference)
     * @return 200 OK with a status acknowledgement map
     */
    @PostMapping(value = "/{onboardingId}/authorized-signers",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit Authorized Signers",
               description = "Submit authorized signers for the business (legal representatives, "
                   + "power of attorney holders). "
                   + "Sends a signal to advance the journey past the signers gate.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authorized signers accepted and workflow advanced",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "400", description = "Invalid signers payload",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "Onboarding journey not found",
            content = @Content),
        @ApiResponse(responseCode = "409", description = "Journey is not at the authorized-signers gate",
            content = @Content)
    })
    public Mono<ResponseEntity<Map<String, Object>>> submitAuthorizedSigners(
            @PathVariable UUID onboardingId,
            @Valid @RequestBody SubmitAuthorizedSignersCommand command) {
        return onboardingService.submitAuthorizedSigners(onboardingId, command)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_AUTHORIZED_SIGNERS_SUBMITTED)));
    }

    /**
     * Triggers KYB verification for the onboarding journey.
     *
     * <p>Sends a {@code kyb-triggered} signal to the workflow, advancing it past the KYB gate.
     * The workflow requests KYB verification from the domain KYB service. KYB result processing
     * is asynchronous — poll {@code GET /{id}/kyb/status} for updates.
     *
     * @param onboardingId the unique identifier of the onboarding workflow instance
     * @return 202 Accepted with a status acknowledgement map
     */
    @PostMapping(value = "/{onboardingId}/kyb", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Trigger KYB Verification",
               description = "Trigger Know Your Business verification after all documents and data "
                   + "are submitted. Sends a signal to advance the journey past the KYB gate. "
                   + "KYB processing is asynchronous — poll GET /{id}/kyb/status for updates.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "KYB verification triggered successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "404", description = "Onboarding journey not found",
            content = @Content),
        @ApiResponse(responseCode = "409", description = "Journey is not at the KYB trigger gate",
            content = @Content)
    })
    public Mono<ResponseEntity<Map<String, Object>>> triggerKybVerification(
            @PathVariable UUID onboardingId) {
        return onboardingService.triggerKybVerification(onboardingId)
                .thenReturn(ResponseEntity.accepted().body(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_KYB_TRIGGERED)));
    }

    /**
     * Retrieves the KYB verification status for the onboarding journey.
     *
     * <p>Returns the KYB case ID and current verification status derived from the workflow's
     * execution state. Possible status values: {@code NOT_STARTED}, {@code CASE_OPENED},
     * {@code IN_PROGRESS}, {@code VERIFIED}.
     *
     * @param onboardingId the unique identifier of the onboarding workflow instance
     * @return 200 OK with the {@link KybStatusResponse}
     */
    @GetMapping(value = "/{onboardingId}/kyb/status",
                produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get KYB Status",
               description = "Retrieve the KYB verification status for the business onboarding journey. "
                   + "Possible values: NOT_STARTED, CASE_OPENED, IN_PROGRESS, VERIFIED.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "KYB status retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = KybStatusResponse.class))),
        @ApiResponse(responseCode = "404", description = "Onboarding journey not found",
            content = @Content)
    })
    public Mono<ResponseEntity<KybStatusResponse>> getKybStatus(
            @PathVariable UUID onboardingId) {
        return onboardingService.getKybStatus(onboardingId)
                .map(KybStatusResponse::from)
                .map(ResponseEntity::ok);
    }

    /**
     * Completes the business onboarding process.
     *
     * <p>Sends a {@code completion-requested} signal to the workflow. The workflow verifies
     * that the KYB case is approved, then activates the business party and sends a
     * completion notification. Fails with 409 if KYB is not yet verified.
     *
     * @param onboardingId the unique identifier of the onboarding workflow instance
     * @return 200 OK with a status acknowledgement map
     */
    @PostMapping(value = "/{onboardingId}/completion",
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Complete Onboarding",
               description = "Complete the business onboarding process — verifies KYB approval, "
                   + "activates the business party, and sends a completion notification. "
                   + "Fails with 409 if KYB has not been verified yet.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Onboarding completed successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "404", description = "Onboarding journey not found",
            content = @Content),
        @ApiResponse(responseCode = "409", description = "KYB not yet verified — cannot complete",
            content = @Content)
    })
    public Mono<ResponseEntity<Map<String, Object>>> completeOnboarding(
            @PathVariable UUID onboardingId) {
        return onboardingService.completeOnboarding(onboardingId)
                .thenReturn(ResponseEntity.ok(Map.of(
                        KEY_ONBOARDING_ID, (Object) onboardingId,
                        KEY_STATUS, STATUS_COMPLETED)));
    }
}
