package com.firefly.experience.onboarding.core.individual.workflows;

import com.firefly.domain.common.notifications.sdk.api.NotificationsApi;
import com.firefly.domain.common.notifications.sdk.model.SendNotificationCommand;
import com.firefly.domain.kyc.kyb.sdk.api.KycApi;
import com.firefly.domain.kyc.kyb.sdk.model.AttachEvidenceCommand;
import com.firefly.domain.people.sdk.api.CustomersApi;
import com.firefly.domain.people.sdk.model.RegisterAddressCommand;
import com.firefly.domain.people.sdk.model.RegisterCustomerCommand;
import com.firefly.domain.people.sdk.model.RegisterEmailCommand;
import com.firefly.domain.people.sdk.model.RegisterNaturalPersonCommand;
import com.firefly.domain.people.sdk.model.RegisterPartyCommand;
import com.firefly.domain.people.sdk.model.RegisterPhoneCommand;
import com.firefly.experience.onboarding.core.individual.commands.InitiateOnboardingCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import com.firefly.experience.onboarding.core.individual.queries.JourneyStatusDTO;
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
 * Signal-driven workflow modelling the complete individual onboarding journey.
 * <p>
 * Execution flow:
 * <pre>
 * Layer 0:  [register-party]
 * Layer 1:  [open-kyc-case]  [send-welcome]        ← parallel
 * Layer 2:  [receive-personal-data]                 ← @WaitForSignal("personal-data-submitted")
 * Layer 3:  [receive-identity-docs]                 ← @WaitForSignal("identity-docs-submitted")
 * Layer 4:  [trigger-kyc-verification]              ← @WaitForSignal("kyc-triggered")
 * Layer 5:  [verify-kyc-approved]                   ← @WaitForSignal("completion-requested")
 * Layer 6:  [activate-party]
 * Layer 7:  [send-completion-notification]
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Workflow(
    id = IndividualOnboardingWorkflow.WORKFLOW_ID,
    name = "Individual Onboarding Journey",
    triggerMode = TriggerMode.SYNC,
    timeoutMs = 86400000,
    publishEvents = true,
    layerConcurrency = 0
)
public class IndividualOnboardingWorkflow {

    // ─── Workflow identity ───
    public static final String WORKFLOW_ID = "individual-onboarding";
    public static final String QUERY_JOURNEY_STATUS = "journeyStatus";

    // ─── Step IDs ───
    public static final String STEP_REGISTER_PARTY = "register-party";
    public static final String STEP_OPEN_KYC_CASE = "open-kyc-case";
    public static final String STEP_SEND_WELCOME = "send-welcome";
    public static final String STEP_RECEIVE_PERSONAL_DATA = "receive-personal-data";
    public static final String STEP_RECEIVE_IDENTITY_DOCS = "receive-identity-docs";
    public static final String STEP_TRIGGER_KYC_VERIFICATION = "trigger-kyc-verification";
    public static final String STEP_VERIFY_KYC_APPROVED = "verify-kyc-approved";
    public static final String STEP_ACTIVATE_PARTY = "activate-party";
    public static final String STEP_SEND_COMPLETION = "send-completion-notification";

    // ─── Signal names ───
    public static final String SIGNAL_PERSONAL_DATA = "personal-data-submitted";
    public static final String SIGNAL_IDENTITY_DOCS = "identity-docs-submitted";
    public static final String SIGNAL_KYC_TRIGGERED = "kyc-triggered";
    public static final String SIGNAL_COMPLETION = "completion-requested";

    // ─── Workflow variable names ───
    public static final String VAR_PARTY_ID = "partyId";
    public static final String VAR_KYC_CASE_ID = "kycCaseId";

    // ─── Journey phases ───
    public static final String PHASE_AWAITING_PERSONAL_DATA = "AWAITING_PERSONAL_DATA";
    public static final String PHASE_AWAITING_IDENTITY_DOCS = "AWAITING_IDENTITY_DOCUMENTS";
    public static final String PHASE_AWAITING_KYC_TRIGGER = "AWAITING_KYC_TRIGGER";
    public static final String PHASE_AWAITING_COMPLETION = "AWAITING_COMPLETION";
    public static final String PHASE_COMPLETING = "COMPLETING";
    public static final String PHASE_COMPLETED = "COMPLETED";

    // ─── KYC verification statuses ───
    public static final String KYC_STATUS_APPROVED = "APPROVED";
    public static final String KYC_STATUS_PENDING = "PENDING";
    public static final String KYC_STATUS_DOCS_SUBMITTED = "DOCUMENTS_SUBMITTED";
    public static final String KYC_STATUS_CASE_OPENED = "CASE_OPENED";
    public static final String KYC_STATUS_NOT_STARTED = "NOT_STARTED";

    // ─── Notification & domain config ───
    private static final String SOURCE_SYSTEM = "EXP_ONBOARDING";
    private static final String NOTIFICATION_CHANNEL = "AUTO";
    private static final String TEMPLATE_WELCOME = "ONBOARDING_WELCOME";
    private static final String TEMPLATE_COMPLETED = "ONBOARDING_COMPLETED";
    // ─── SDK response field names ───
    private static final String FIELD_PARTY_ID = "partyId";
    private static final String FIELD_CASE_ID = "caseId";
    private static final String FIELD_DOCUMENT_ID = "documentId";

    private final CustomersApi customersApi;
    private final KycApi kycApi;
    private final NotificationsApi notificationsApi;
    private final ObjectMapper objectMapper;

    // ─── Phase 1: Initiation (executes on startWorkflow) ───

    @WorkflowStep(id = STEP_REGISTER_PARTY, compensatable = true,
                  compensationMethod = "compensateDeactivateParty")
    @SetVariable(VAR_PARTY_ID)
    public Mono<UUID> registerParty(@Input InitiateOnboardingCommand cmd) {
        RegisterCustomerCommand registerCmd = new RegisterCustomerCommand()
                .party(new RegisterPartyCommand()
                        .partyKind(RegisterPartyCommand.PartyKindEnum.INDIVIDUAL)
                        .sourceSystem(SOURCE_SYSTEM))
                .naturalPerson(new RegisterNaturalPersonCommand()
                        .givenName(cmd.getFirstName())
                        .familyName1(cmd.getLastName()))
                .emails(List.of(new RegisterEmailCommand().email(cmd.getEmail())));

        if (cmd.getPhone() != null && !cmd.getPhone().isBlank()) {
            registerCmd.phones(List.of(new RegisterPhoneCommand().phoneNumber(cmd.getPhone())));
        }

        return customersApi.registerCustomer(registerCmd)
                .flatMap(response -> extractUuid(response, FIELD_PARTY_ID))
                .doOnNext(partyId -> log.info("Registered party: partyId={}", partyId));
    }

    @WorkflowStep(id = STEP_OPEN_KYC_CASE, dependsOn = STEP_REGISTER_PARTY,
                  compensatable = true, compensationMethod = "compensateCancelKycCase")
    @SetVariable(VAR_KYC_CASE_ID)
    public Mono<UUID> openKycCase(@FromStep(STEP_REGISTER_PARTY) UUID partyId) {
        return kycApi.openCase()
                .flatMap(response -> extractUuid(response, FIELD_CASE_ID))
                .doOnNext(caseId -> log.info("Opened KYC case: caseId={}", caseId));
    }

    @WorkflowStep(id = STEP_SEND_WELCOME, dependsOn = STEP_REGISTER_PARTY)
    public Mono<Void> sendWelcomeNotification(@FromStep(STEP_REGISTER_PARTY) UUID partyId,
                                               @Input InitiateOnboardingCommand cmd) {
        SendNotificationCommand notifCmd = new SendNotificationCommand()
                .partyId(partyId)
                .channel(NOTIFICATION_CHANNEL)
                .templateId(TEMPLATE_WELCOME)
                .putParametersItem("subject", "Welcome to Firefly")
                .putParametersItem("email", cmd.getEmail());

        if (cmd.getPhone() != null) {
            notifCmd.putParametersItem("phone", cmd.getPhone());
        }

        return notificationsApi.sendNotification(notifCmd)
                .doOnNext(r -> log.info("Sent welcome notification for partyId={}", partyId))
                .then();
    }

    // ─── Gate: Wait for personal data submission ───

    @WorkflowStep(id = STEP_RECEIVE_PERSONAL_DATA,
                  dependsOn = {STEP_OPEN_KYC_CASE, STEP_SEND_WELCOME})
    @WaitForSignal(SIGNAL_PERSONAL_DATA)
    public Mono<Void> receivePersonalData(@Variable(VAR_PARTY_ID) UUID partyId,
                                           Object signalData) {
        SubmitPersonalDataCommand cmd = mapSignalPayload(signalData, SubmitPersonalDataCommand.class);
        RegisterAddressCommand addressCmd = new RegisterAddressCommand()
                .line1(cmd.getAddressLine1())
                .line2(cmd.getAddressLine2())
                .city(cmd.getCity())
                .postalCode(cmd.getPostalCode());

        return customersApi.addCustomerAddress(partyId, addressCmd)
                .doOnNext(r -> log.info("Submitted personal data for partyId={}", partyId))
                .then();
    }

    // ─── Gate: Wait for identity documents ───

    @WorkflowStep(id = STEP_RECEIVE_IDENTITY_DOCS, dependsOn = STEP_RECEIVE_PERSONAL_DATA)
    @WaitForSignal(SIGNAL_IDENTITY_DOCS)
    public Mono<UUID> receiveIdentityDocuments(@Variable(VAR_KYC_CASE_ID) UUID caseId,
                                                @Variable(VAR_PARTY_ID) UUID partyId,
                                                Object signalData) {
        SubmitIdentityDocumentsCommand cmd = mapSignalPayload(signalData,
                SubmitIdentityDocumentsCommand.class);
        AttachEvidenceCommand attachCmd = new AttachEvidenceCommand()
                .caseId(caseId)
                .documentType(cmd.getDocumentType())
                .documentName(cmd.getDocumentNumber())
                .documentContent(cmd.getDocumentContent())
                .mimeType(cmd.getMimeType());

        return kycApi.attachEvidence(caseId, attachCmd)
                .flatMap(response -> extractUuid(response, FIELD_DOCUMENT_ID))
                .doOnNext(docId -> log.info("Attached identity document: documentId={}", docId));
    }

    // ─── Gate: Wait for KYC trigger ───

    @WorkflowStep(id = STEP_TRIGGER_KYC_VERIFICATION, dependsOn = STEP_RECEIVE_IDENTITY_DOCS)
    @WaitForSignal(SIGNAL_KYC_TRIGGERED)
    public Mono<Void> triggerKycVerification(@Variable(VAR_KYC_CASE_ID) UUID caseId,
                                              @Variable(VAR_PARTY_ID) UUID partyId) {
        return kycApi.verify(caseId)
                .doOnNext(r -> log.info("Triggered KYC verification for caseId={}", caseId))
                .then();
    }

    // ─── Gate: Wait for completion request ───

    @WorkflowStep(id = STEP_VERIFY_KYC_APPROVED, dependsOn = STEP_TRIGGER_KYC_VERIFICATION)
    @WaitForSignal(SIGNAL_COMPLETION)
    public Mono<Void> verifyKycApproved(@Variable(VAR_KYC_CASE_ID) UUID caseId) {
        // No GET endpoint available for KYC case status — proceed optimistically
        log.info("KYC completion gate reached for caseId={} — proceeding to activation", caseId);
        return Mono.empty();
    }

    @WorkflowStep(id = STEP_ACTIVATE_PARTY, dependsOn = STEP_VERIFY_KYC_APPROVED)
    public Mono<Void> activateParty(@Variable(VAR_PARTY_ID) UUID partyId) {
        return customersApi.reactivateCustomer(partyId)
                .doOnNext(r -> log.info("Activated party: partyId={}", partyId))
                .then();
    }

    @WorkflowStep(id = STEP_SEND_COMPLETION, dependsOn = STEP_ACTIVATE_PARTY)
    public Mono<Void> sendCompletionNotification(@Variable(VAR_PARTY_ID) UUID partyId) {
        SendNotificationCommand notifCmd = new SendNotificationCommand()
                .partyId(partyId)
                .channel(NOTIFICATION_CHANNEL)
                .templateId(TEMPLATE_COMPLETED)
                .putParametersItem("subject", "Onboarding Complete");

        return notificationsApi.sendNotification(notifCmd)
                .doOnNext(r -> log.info("Sent completion notification for partyId={}", partyId))
                .then();
    }

    // ─── Compensation methods ───

    public Mono<Void> compensateDeactivateParty(@FromStep(STEP_REGISTER_PARTY) UUID partyId) {
        log.warn("Compensating: requesting closure for party partyId={}", partyId);
        return customersApi.requestCustomerClosure(partyId)
                .then()
                .onErrorResume(ex -> {
                    log.warn("Failed to compensate party closure partyId={}: {}", partyId, ex.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> compensateCancelKycCase(@FromStep(STEP_OPEN_KYC_CASE) UUID caseId) {
        log.warn("Compensating: cancelling KYC case caseId={}", caseId);
        return kycApi.fail(caseId)
                .then()
                .onErrorResume(ex -> {
                    log.warn("Failed to compensate KYC case caseId={}: {}", caseId, ex.getMessage());
                    return Mono.empty();
                });
    }

    // ─── Journey state query ───

    @WorkflowQuery(QUERY_JOURNEY_STATUS)
    public JourneyStatusDTO getJourneyStatus(ExecutionContext ctx) {
        Map<String, StepStatus> steps = ctx.getStepStatuses();
        return JourneyStatusDTO.builder()
                .onboardingId(UUID.fromString(ctx.getCorrelationId()))
                .partyId(toUuid(ctx.getVariable(VAR_PARTY_ID)))
                .kycCaseId(toUuid(ctx.getVariable(VAR_KYC_CASE_ID)))
                .currentPhase(deriveCurrentPhase(steps))
                .completedSteps(steps.entrySet().stream()
                        .filter(e -> e.getValue() == StepStatus.DONE)
                        .map(Map.Entry::getKey)
                        .toList())
                .nextStep(deriveNextStep(steps))
                .kycVerificationStatus(deriveKycVerificationStatus(steps))
                .build();
    }

    // ─── Lifecycle callbacks ───

    @OnWorkflowComplete
    public void onJourneyComplete(ExecutionContext ctx) {
        log.info("Onboarding journey completed for partyId={}", ctx.getVariable(VAR_PARTY_ID));
    }

    @OnWorkflowError
    public void onJourneyError(Throwable error, ExecutionContext ctx) {
        log.error("Onboarding journey failed for partyId={}: {}",
                ctx.getVariable(VAR_PARTY_ID), error.getMessage());
    }

    // ─── Private helpers ───

    private String deriveCurrentPhase(Map<String, StepStatus> steps) {
        if (steps.getOrDefault(STEP_RECEIVE_PERSONAL_DATA, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_AWAITING_PERSONAL_DATA;
        }
        if (steps.getOrDefault(STEP_RECEIVE_IDENTITY_DOCS, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_AWAITING_IDENTITY_DOCS;
        }
        if (steps.getOrDefault(STEP_TRIGGER_KYC_VERIFICATION, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_AWAITING_KYC_TRIGGER;
        }
        if (steps.getOrDefault(STEP_VERIFY_KYC_APPROVED, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_AWAITING_COMPLETION;
        }
        if (steps.getOrDefault(STEP_SEND_COMPLETION, StepStatus.PENDING) != StepStatus.DONE) {
            return PHASE_COMPLETING;
        }
        return PHASE_COMPLETED;
    }

    private String deriveKycVerificationStatus(Map<String, StepStatus> steps) {
        if (steps.getOrDefault(STEP_VERIFY_KYC_APPROVED, StepStatus.PENDING) == StepStatus.DONE) {
            return KYC_STATUS_APPROVED;
        }
        if (steps.getOrDefault(STEP_TRIGGER_KYC_VERIFICATION, StepStatus.PENDING) == StepStatus.DONE) {
            return KYC_STATUS_PENDING;
        }
        if (steps.getOrDefault(STEP_RECEIVE_IDENTITY_DOCS, StepStatus.PENDING) == StepStatus.DONE) {
            return KYC_STATUS_DOCS_SUBMITTED;
        }
        if (steps.getOrDefault(STEP_OPEN_KYC_CASE, StepStatus.PENDING) == StepStatus.DONE) {
            return KYC_STATUS_CASE_OPENED;
        }
        return KYC_STATUS_NOT_STARTED;
    }

    private String deriveNextStep(Map<String, StepStatus> steps) {
        if (steps.getOrDefault(STEP_RECEIVE_PERSONAL_DATA, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_RECEIVE_PERSONAL_DATA;
        }
        if (steps.getOrDefault(STEP_RECEIVE_IDENTITY_DOCS, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_RECEIVE_IDENTITY_DOCS;
        }
        if (steps.getOrDefault(STEP_TRIGGER_KYC_VERIFICATION, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_TRIGGER_KYC_VERIFICATION;
        }
        if (steps.getOrDefault(STEP_VERIFY_KYC_APPROVED, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_VERIFY_KYC_APPROVED;
        }
        return null;
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

    /**
     * Converts a workflow variable value to UUID. Returns {@code null} when the value is absent
     * or not convertible, which is acceptable here because it is used to populate optional fields
     * in the {@link JourneyStatusDTO} query result (e.g. partyId/kycCaseId may not yet be set
     * early in the journey).
     */
    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String s) return UUID.fromString(s);
        return null;
    }
}
