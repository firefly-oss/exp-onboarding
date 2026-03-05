package com.firefly.experience.onboarding.core.business.workflows;

import com.firefly.domain.common.notifications.sdk.api.NotificationsApi;
import com.firefly.domain.common.notifications.sdk.model.SendNotificationCommand;
import com.firefly.domain.kyc.kyb.sdk.api.KybApi;
import com.firefly.domain.kyc.kyb.sdk.model.AttachKybEvidenceCommand;
import com.firefly.domain.kyc.kyb.sdk.model.OpenKybCaseCommand;
import com.firefly.domain.kyc.kyb.sdk.model.FailKybCommand;
import com.firefly.domain.kyc.kyb.sdk.model.VerifyKybCommand;
import com.firefly.domain.people.sdk.api.BusinessesApi;
import com.firefly.domain.people.sdk.model.RegisterAddressCommand;
import com.firefly.domain.people.sdk.model.RegisterBusinessCommand;
import com.firefly.domain.people.sdk.model.RegisterLegalEntityCommand;
import com.firefly.domain.people.sdk.model.RegisterPartyCommand;
import com.firefly.domain.people.sdk.model.UpdateBusinessCommand;
import com.firefly.experience.onboarding.core.business.commands.InitiateBusinessOnboardingCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCorporateDocumentsCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitUbosCommand;
import com.firefly.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.orchestration.core.argument.FromStep;
import org.fireflyframework.orchestration.core.argument.Input;
import org.fireflyframework.orchestration.core.argument.SetVariable;
import org.fireflyframework.orchestration.core.argument.Variable;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.core.model.StepStatus;
import org.fireflyframework.orchestration.core.model.TriggerMode;
import org.fireflyframework.orchestration.workflow.annotation.OnWorkflowComplete;
import org.fireflyframework.orchestration.workflow.annotation.OnWorkflowError;
import org.fireflyframework.orchestration.workflow.annotation.WaitForSignal;
import org.fireflyframework.orchestration.workflow.annotation.Workflow;
import org.fireflyframework.orchestration.workflow.annotation.WorkflowQuery;
import org.fireflyframework.orchestration.workflow.annotation.WorkflowStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Signal-driven workflow modelling the complete business onboarding journey (Persona Jurídica).
 * <p>
 * Execution flow:
 * <pre>
 * Layer 0:  [register-business-party]
 * Layer 1:  [open-kyb-case]  [send-welcome-notification]     ← parallel
 * Layer 2:  [receive-company-data]                            ← @WaitForSignal("company-data-submitted")
 * Layer 3:  [receive-ubos]                                    ← @WaitForSignal("ubos-submitted")
 * Layer 4:  [receive-corporate-documents]                     ← @WaitForSignal("corporate-documents-submitted")
 * Layer 5:  [receive-authorized-signers]                      ← @WaitForSignal("authorized-signers-submitted")
 * Layer 6:  [trigger-kyb-verification]                        ← @WaitForSignal("kyb-triggered")
 * Layer 7:  [verify-kyb-approved]                             ← @WaitForSignal("completion-requested")
 * Layer 8:  [activate-business-party]
 * Layer 9:  [send-completion-notification]
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Workflow(
    id = BusinessOnboardingWorkflow.WORKFLOW_ID,
    name = "Business Onboarding Journey",
    triggerMode = TriggerMode.SYNC,
    timeoutMs = 172800000,
    publishEvents = true,
    layerConcurrency = 0
)
public class BusinessOnboardingWorkflow {

    // ─── Workflow identity ───
    public static final String WORKFLOW_ID = "business-onboarding";
    public static final String QUERY_JOURNEY_STATUS = "journeyStatus";

    // ─── Step IDs ───
    public static final String STEP_REGISTER_PARTY = "register-business-party";
    public static final String STEP_OPEN_KYB_CASE = "open-kyb-case";
    public static final String STEP_SEND_WELCOME = "send-welcome-notification";
    public static final String STEP_RECEIVE_COMPANY_DATA = "receive-company-data";
    public static final String STEP_RECEIVE_UBOS = "receive-ubos";
    public static final String STEP_RECEIVE_CORPORATE_DOCS = "receive-corporate-documents";
    public static final String STEP_RECEIVE_AUTHORIZED_SIGNERS = "receive-authorized-signers";
    public static final String STEP_TRIGGER_KYB = "trigger-kyb-verification";
    public static final String STEP_VERIFY_KYB_APPROVED = "verify-kyb-approved";
    public static final String STEP_ACTIVATE_PARTY = "activate-business-party";
    public static final String STEP_SEND_COMPLETION = "send-completion-notification";

    // ─── Signal names ───
    public static final String SIGNAL_COMPANY_DATA = "company-data-submitted";
    public static final String SIGNAL_UBOS = "ubos-submitted";
    public static final String SIGNAL_CORPORATE_DOCS = "corporate-documents-submitted";
    public static final String SIGNAL_AUTHORIZED_SIGNERS = "authorized-signers-submitted";
    public static final String SIGNAL_KYB_TRIGGERED = "kyb-triggered";
    public static final String SIGNAL_COMPLETION = "completion-requested";

    // ─── Workflow variable names ───
    public static final String VAR_PARTY_ID = "partyId";
    public static final String VAR_KYB_CASE_ID = "kybCaseId";

    // ─── Journey phases ───
    public static final String PHASE_INITIATED = "INITIATED";
    public static final String PHASE_COMPANY_DATA_RECEIVED = "COMPANY_DATA_RECEIVED";
    public static final String PHASE_UBOS_RECEIVED = "UBOS_RECEIVED";
    public static final String PHASE_DOCUMENTS_RECEIVED = "DOCUMENTS_RECEIVED";
    public static final String PHASE_SIGNERS_RECEIVED = "SIGNERS_RECEIVED";
    public static final String PHASE_KYB_IN_PROGRESS = "KYB_IN_PROGRESS";
    public static final String PHASE_PENDING_COMPLETION = "PENDING_COMPLETION";
    public static final String PHASE_COMPLETING = "COMPLETING";
    public static final String PHASE_COMPLETED = "COMPLETED";

    // ─── Notification & domain config ───
    private static final String SOURCE_SYSTEM = "EXP_ONBOARDING";
    private static final String NOTIFICATION_CHANNEL = "AUTO";
    private static final String TEMPLATE_BUSINESS_WELCOME = "BUSINESS_ONBOARDING_WELCOME";
    private static final String TEMPLATE_BUSINESS_COMPLETED = "BUSINESS_ONBOARDING_COMPLETED";

    // ─── SDK response field names ───
    private static final String FIELD_PARTY_ID = "partyId";
    private static final String FIELD_CASE_ID = "caseId";

    private final BusinessesApi businessesApi;
    private final KybApi kybApi;
    private final NotificationsApi notificationsApi;
    private final ObjectMapper objectMapper;

    // ─── Phase 1: Initiation (executes on startWorkflow) ───

    @WorkflowStep(id = STEP_REGISTER_PARTY, compensatable = true,
                  compensationMethod = "compensateDeactivateParty")
    @SetVariable(VAR_PARTY_ID)
    public Mono<UUID> registerBusinessParty(@Input InitiateBusinessOnboardingCommand cmd) {
        RegisterBusinessCommand registerCmd = new RegisterBusinessCommand()
                .party(new RegisterPartyCommand()
                        .partyKind(RegisterPartyCommand.PartyKindEnum.ORGANIZATION)
                        .sourceSystem(SOURCE_SYSTEM))
                .legalEntity(new RegisterLegalEntityCommand()
                        .legalName(cmd.getBusinessName())
                        .registrationNumber(cmd.getRegistrationNumber()));

        return businessesApi.registerBusiness(registerCmd, UUID.randomUUID().toString(), null, null, null, null, null, null)
                .flatMap(response -> extractUuid(response, FIELD_PARTY_ID))
                .doOnNext(partyId -> log.info("Registered business party: partyId={}", partyId));
    }

    @WorkflowStep(id = STEP_OPEN_KYB_CASE, dependsOn = STEP_REGISTER_PARTY,
                  compensatable = true, compensationMethod = "compensateCancelKybCase")
    @SetVariable(VAR_KYB_CASE_ID)
    public Mono<UUID> openKybCase(@FromStep(STEP_REGISTER_PARTY) UUID partyId) {
        OpenKybCaseCommand openCmd = new OpenKybCaseCommand()
                .partyId(partyId);

        return kybApi.startKyb(openCmd, UUID.randomUUID().toString(), null, null, null, null, null, null)
                .flatMap(response -> extractUuid(response, FIELD_CASE_ID))
                .doOnNext(caseId -> log.info("Opened KYB case: caseId={}", caseId));
    }

    @WorkflowStep(id = STEP_SEND_WELCOME, dependsOn = STEP_REGISTER_PARTY)
    public Mono<Void> sendWelcomeNotification(@FromStep(STEP_REGISTER_PARTY) UUID partyId,
                                               @Input InitiateBusinessOnboardingCommand cmd) {
        SendNotificationCommand notifCmd = new SendNotificationCommand()
                .partyId(partyId)
                .channel(NOTIFICATION_CHANNEL)
                .templateCode(TEMPLATE_BUSINESS_WELCOME)
                .subject("Welcome to Firefly Business Banking");

        if (cmd.getContactEmail() != null) {
            notifCmd.recipientEmail(cmd.getContactEmail());
        }
        if (cmd.getContactPhone() != null) {
            notifCmd.recipientPhone(cmd.getContactPhone());
        }

        return notificationsApi.sendNotification(notifCmd)
                .doOnNext(r -> log.info("Sent business welcome notification for partyId={}", partyId))
                .then();
    }

    // ─── Gate: Wait for company data submission ───

    @WorkflowStep(id = STEP_RECEIVE_COMPANY_DATA,
                  dependsOn = {STEP_OPEN_KYB_CASE, STEP_SEND_WELCOME})
    @WaitForSignal(SIGNAL_COMPANY_DATA)
    public Mono<Void> receiveCompanyData(@Variable(VAR_PARTY_ID) UUID partyId,
                                          Object signalData) {
        SubmitCompanyDataCommand cmd = mapSignalPayload(signalData, SubmitCompanyDataCommand.class);

        // Update the legal entity with full company details via domain SDK
        UpdateBusinessCommand updateCmd = new UpdateBusinessCommand()
                .partyId(partyId)
                .legalName(cmd.getLegalName())
                .tradeName(cmd.getTradeName())
                .incorporationDate(cmd.getIncorporationDate())
                .taxIdNumber(cmd.getTaxId())
                .industryDescription(cmd.getBusinessActivity());

        Mono<Void> updateLegalEntity = businessesApi.updateBusiness(updateCmd,
                        UUID.randomUUID().toString(), null, null, null, null, null, null)
                .then();

        // Register business address
        RegisterAddressCommand addressCmd = new RegisterAddressCommand()
                .line1(cmd.getAddressLine1())
                .line2(cmd.getAddressLine2())
                .city(cmd.getCity())
                .postalCode(cmd.getPostalCode());

        Mono<Void> addAddress = businessesApi.addBusinessAddress(partyId, addressCmd,
                        UUID.randomUUID().toString(), null, null, null, null, null, null)
                .then();

        return updateLegalEntity.then(addAddress)
                .doOnSuccess(v -> log.info("Submitted company data for partyId={}", partyId));
    }

    // ─── Gate: Wait for UBOs submission ───

    @WorkflowStep(id = STEP_RECEIVE_UBOS, dependsOn = STEP_RECEIVE_COMPANY_DATA)
    @WaitForSignal(SIGNAL_UBOS)
    public Mono<Void> receiveUbos(@Variable(VAR_PARTY_ID) UUID partyId,
                                   @Variable(VAR_KYB_CASE_ID) UUID caseId,
                                   Object signalData) {
        SubmitUbosCommand cmd = mapSignalPayload(signalData, SubmitUbosCommand.class);

        if (cmd.getUbos() == null || cmd.getUbos().isEmpty()) {
            return Mono.empty();
        }

        // Register each UBO as evidence in the KYB case via domain SDK
        return Mono.when(cmd.getUbos().stream()
                .map(ubo -> {
                    AttachKybEvidenceCommand attachCmd = new AttachKybEvidenceCommand()
                            .caseId(caseId)
                            .partyId(partyId)
                            .documentType("UBO_DECLARATION")
                            .documentName(ubo.getFirstName() + " " + ubo.getLastName()
                                    + " - " + ubo.getOwnershipPercentage() + "%");
                    return kybApi.attachKybEvidence(caseId, attachCmd,
                            UUID.randomUUID().toString(), null, null, null, null, null, null);
                })
                .toList())
                .doOnSuccess(v -> log.info("Registered {} UBOs for partyId={}, caseId={}",
                        cmd.getUbos().size(), partyId, caseId));
    }

    // ─── Gate: Wait for corporate documents ───

    @WorkflowStep(id = STEP_RECEIVE_CORPORATE_DOCS, dependsOn = STEP_RECEIVE_UBOS)
    @WaitForSignal(SIGNAL_CORPORATE_DOCS)
    public Mono<Void> receiveCorporateDocuments(@Variable(VAR_KYB_CASE_ID) UUID caseId,
                                                 @Variable(VAR_PARTY_ID) UUID partyId,
                                                 Object signalData) {
        SubmitCorporateDocumentsCommand cmd = mapSignalPayload(signalData, SubmitCorporateDocumentsCommand.class);

        if (cmd.getDocuments() == null || cmd.getDocuments().isEmpty()) {
            return Mono.empty();
        }

        // Attach each document to the KYB case via domain SDK
        return Mono.when(cmd.getDocuments().stream()
                .map(doc -> {
                    AttachKybEvidenceCommand attachCmd = new AttachKybEvidenceCommand()
                            .caseId(caseId)
                            .partyId(partyId)
                            .documentType(doc.getDocumentType())
                            .documentName(doc.getDocumentReference());
                    return kybApi.attachKybEvidence(caseId, attachCmd, UUID.randomUUID().toString(), null, null, null, null, null, null);
                })
                .toList())
                .doOnSuccess(v -> log.info("Attached {} corporate documents for caseId={}",
                        cmd.getDocuments().size(), caseId));
    }

    // ─── Gate: Wait for authorized signers ───

    @WorkflowStep(id = STEP_RECEIVE_AUTHORIZED_SIGNERS, dependsOn = STEP_RECEIVE_CORPORATE_DOCS)
    @WaitForSignal(SIGNAL_AUTHORIZED_SIGNERS)
    public Mono<Void> receiveAuthorizedSigners(@Variable(VAR_PARTY_ID) UUID partyId,
                                                @Variable(VAR_KYB_CASE_ID) UUID caseId,
                                                Object signalData) {
        SubmitAuthorizedSignersCommand cmd = mapSignalPayload(signalData, SubmitAuthorizedSignersCommand.class);

        if (cmd.getSigners() == null || cmd.getSigners().isEmpty()) {
            return Mono.empty();
        }

        // Register each authorized signer as evidence in the KYB case
        return Mono.when(cmd.getSigners().stream()
                .map(signer -> {
                    AttachKybEvidenceCommand attachCmd = new AttachKybEvidenceCommand()
                            .caseId(caseId)
                            .partyId(partyId)
                            .documentType("AUTHORIZED_SIGNER")
                            .documentName(signer.getFirstName() + " " + signer.getLastName()
                                    + " - " + signer.getRole());
                    return kybApi.attachKybEvidence(caseId, attachCmd,
                            UUID.randomUUID().toString(), null, null, null, null, null, null);
                })
                .toList())
                .doOnSuccess(v -> log.info("Registered {} authorized signers for partyId={}, caseId={}",
                        cmd.getSigners().size(), partyId, caseId));
    }

    // ─── Gate: Wait for KYB verification trigger ───

    @WorkflowStep(id = STEP_TRIGGER_KYB, dependsOn = STEP_RECEIVE_AUTHORIZED_SIGNERS)
    @WaitForSignal(SIGNAL_KYB_TRIGGERED)
    public Mono<Void> triggerKybVerification(@Variable(VAR_KYB_CASE_ID) UUID caseId,
                                              @Variable(VAR_PARTY_ID) UUID partyId) {
        VerifyKybCommand verifyCmd = new VerifyKybCommand()
                .caseId(caseId)
                .partyId(partyId);

        return kybApi.verifyKyb(caseId, verifyCmd, UUID.randomUUID().toString(), null, null, null, null, null, null)
                .doOnNext(r -> log.info("Triggered KYB verification for caseId={}", caseId))
                .then();
    }

    // ─── Gate: Wait for completion request ───

    @WorkflowStep(id = STEP_VERIFY_KYB_APPROVED, dependsOn = STEP_TRIGGER_KYB)
    @WaitForSignal(SIGNAL_COMPLETION)
    public Mono<Void> verifyKybApproved(@Variable(VAR_KYB_CASE_ID) UUID caseId) {
        return kybApi.getKybResult(caseId, null, null, null, null, null, null)
                .flatMap(response -> {
                    if (response instanceof Map<?, ?> map) {
                        String status = String.valueOf(map.get("verificationStatus"));
                        if ("VERIFIED".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status)
                                || "true".equalsIgnoreCase(String.valueOf(map.get("success")))) {
                            log.info("KYB verified for caseId={}", caseId);
                            return Mono.<Void>empty();
                        }
                        return Mono.<Void>error(new BusinessException(
                                HttpStatus.CONFLICT, "KYB_NOT_VERIFIED",
                                "KYB not yet verified. Current status: " + status));
                    }
                    return Mono.<Void>error(new BusinessException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "KYB_STATUS_UNKNOWN",
                            "Unable to determine KYB status"));
                });
    }

    @WorkflowStep(id = STEP_ACTIVATE_PARTY, dependsOn = STEP_VERIFY_KYB_APPROVED)
    public Mono<Void> activateBusinessParty(@Variable(VAR_PARTY_ID) UUID partyId) {
        return businessesApi.reactivateBusiness(partyId, UUID.randomUUID().toString(), null, null, null, null, null, null)
                .doOnNext(r -> log.info("Activated business party: partyId={}", partyId))
                .then();
    }

    @WorkflowStep(id = STEP_SEND_COMPLETION, dependsOn = STEP_ACTIVATE_PARTY)
    public Mono<Void> sendCompletionNotification(@Variable(VAR_PARTY_ID) UUID partyId) {
        SendNotificationCommand notifCmd = new SendNotificationCommand()
                .partyId(partyId)
                .channel(NOTIFICATION_CHANNEL)
                .templateCode(TEMPLATE_BUSINESS_COMPLETED)
                .subject("Business Onboarding Complete");

        return notificationsApi.sendNotification(notifCmd)
                .doOnNext(r -> log.info("Sent business completion notification for partyId={}", partyId))
                .then();
    }

    // ─── Compensation methods ───

    public Mono<Void> compensateDeactivateParty(@FromStep(STEP_REGISTER_PARTY) UUID partyId) {
        log.warn("Compensating: requesting closure for business party partyId={}", partyId);
        return businessesApi.requestBusinessClosure(partyId, UUID.randomUUID().toString(), null, null, null, null, null, null)
                .then()
                .onErrorResume(ex -> {
                    log.warn("Failed to compensate business party closure partyId={}: {}", partyId, ex.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> compensateCancelKybCase(@FromStep(STEP_OPEN_KYB_CASE) UUID caseId) {
        log.warn("Compensating: cancelling KYB case — caseId={}", caseId);
        FailKybCommand failCmd = new FailKybCommand()
                .caseId(caseId)
                .reason("Business onboarding cancelled");
        return kybApi.failKyb(caseId, failCmd, UUID.randomUUID().toString(), null, null, null, null, null, null)
                .then()
                .onErrorResume(ex -> {
                    log.warn("Failed to compensate KYB case caseId={}: {}", caseId, ex.getMessage());
                    return Mono.empty();
                });
    }

    // ─── Journey state query ───

    @WorkflowQuery(QUERY_JOURNEY_STATUS)
    public BusinessOnboardingStatusDTO getJourneyStatus(ExecutionContext ctx) {
        Map<String, StepStatus> steps = ctx.getStepStatuses();
        return BusinessOnboardingStatusDTO.builder()
                .onboardingId(UUID.fromString(ctx.getCorrelationId()))
                .partyId(toUuid(ctx.getVariable(VAR_PARTY_ID)))
                .kybCaseId(toUuid(ctx.getVariable(VAR_KYB_CASE_ID)))
                .currentPhase(deriveCurrentPhase(steps))
                .completedSteps(steps.entrySet().stream()
                        .filter(e -> e.getValue() == StepStatus.DONE)
                        .map(Map.Entry::getKey)
                        .toList())
                .nextStep(deriveNextStep(steps))
                .kybStatus(deriveKybStatus(steps))
                .build();
    }

    // ─── Lifecycle callbacks ───

    @OnWorkflowComplete
    public void onJourneyComplete(ExecutionContext ctx) {
        log.info("Business onboarding completed for partyId={}", ctx.getVariable(VAR_PARTY_ID));
    }

    @OnWorkflowError
    public void onJourneyError(Throwable error, ExecutionContext ctx) {
        log.error("Business onboarding failed for partyId={}: {}",
                ctx.getVariable(VAR_PARTY_ID), error.getMessage());
    }

    // ─── Private helpers ───

    private String deriveCurrentPhase(Map<String, StepStatus> steps) {
        if (steps.getOrDefault(STEP_RECEIVE_COMPANY_DATA, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_INITIATED;
        }
        if (steps.getOrDefault(STEP_RECEIVE_UBOS, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_COMPANY_DATA_RECEIVED;
        }
        if (steps.getOrDefault(STEP_RECEIVE_CORPORATE_DOCS, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_UBOS_RECEIVED;
        }
        if (steps.getOrDefault(STEP_RECEIVE_AUTHORIZED_SIGNERS, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_DOCUMENTS_RECEIVED;
        }
        if (steps.getOrDefault(STEP_TRIGGER_KYB, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_SIGNERS_RECEIVED;
        }
        if (steps.getOrDefault(STEP_VERIFY_KYB_APPROVED, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_KYB_IN_PROGRESS;
        }
        if (steps.getOrDefault(STEP_SEND_COMPLETION, StepStatus.PENDING) != StepStatus.DONE) {
            return PHASE_COMPLETING;
        }
        return PHASE_COMPLETED;
    }

    private String deriveNextStep(Map<String, StepStatus> steps) {
        if (steps.getOrDefault(STEP_RECEIVE_COMPANY_DATA, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_RECEIVE_COMPANY_DATA;
        }
        if (steps.getOrDefault(STEP_RECEIVE_UBOS, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_RECEIVE_UBOS;
        }
        if (steps.getOrDefault(STEP_RECEIVE_CORPORATE_DOCS, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_RECEIVE_CORPORATE_DOCS;
        }
        if (steps.getOrDefault(STEP_RECEIVE_AUTHORIZED_SIGNERS, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_RECEIVE_AUTHORIZED_SIGNERS;
        }
        if (steps.getOrDefault(STEP_TRIGGER_KYB, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_TRIGGER_KYB;
        }
        if (steps.getOrDefault(STEP_VERIFY_KYB_APPROVED, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_VERIFY_KYB_APPROVED;
        }
        return null;
    }

    private String deriveKybStatus(Map<String, StepStatus> steps) {
        if (steps.getOrDefault(STEP_VERIFY_KYB_APPROVED, StepStatus.PENDING) == StepStatus.DONE) {
            return "VERIFIED";
        }
        if (steps.getOrDefault(STEP_TRIGGER_KYB, StepStatus.PENDING) == StepStatus.DONE) {
            return "IN_PROGRESS";
        }
        if (steps.getOrDefault(STEP_OPEN_KYB_CASE, StepStatus.PENDING) == StepStatus.DONE) {
            return "CASE_OPENED";
        }
        return "NOT_STARTED";
    }

    private Mono<UUID> extractUuid(Object response, String field) {
        if (response instanceof Map<?, ?> map && map.get(field) instanceof String s) {
            return Mono.just(UUID.fromString(s));
        }
        return Mono.error(new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "MISSING_FIELD",
                "Expected field '" + field + "' not found in response"));
    }

    private <T> T mapSignalPayload(Object signalData, Class<T> type) {
        return objectMapper.convertValue(signalData, type);
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String s) return UUID.fromString(s);
        return null;
    }
}
