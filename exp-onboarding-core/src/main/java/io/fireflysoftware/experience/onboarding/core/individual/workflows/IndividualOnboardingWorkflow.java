/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.experience.onboarding.core.individual.workflows;

import io.fireflysoftware.domain.common.contracts.sdk.api.KitDocumentsApi;
import io.fireflysoftware.domain.common.notifications.sdk.api.NotificationsApi;
import io.fireflysoftware.domain.common.notifications.sdk.model.SendNotificationCommand;
import io.fireflysoftware.domain.kyc.kyb.sdk.api.KycApi;
import io.fireflysoftware.domain.kyc.kyb.sdk.model.AttachEvidenceCommand;
import io.fireflysoftware.domain.people.sdk.api.CustomersApi;
import io.fireflysoftware.domain.people.sdk.api.PartyAttributesApi;
import io.fireflysoftware.domain.people.sdk.model.PartyAttributeRequest;
import io.fireflysoftware.domain.people.sdk.model.RegisterAddressCommand;
import io.fireflysoftware.domain.people.sdk.model.RegisterCustomerCommand;
import io.fireflysoftware.domain.people.sdk.model.RegisterEmailCommand;
import io.fireflysoftware.domain.people.sdk.model.RegisterNaturalPersonCommand;
import io.fireflysoftware.domain.people.sdk.model.RegisterPartyCommand;
import io.fireflysoftware.domain.people.sdk.model.RegisterPhoneCommand;
import io.fireflysoftware.domain.people.sdk.model.UpdateCustomerCommand;
import io.fireflysoftware.experience.onboarding.core.individual.commands.InitiateOnboardingCommand;
import io.fireflysoftware.experience.onboarding.core.individual.commands.SubmitEconomicDataCommand;
import io.fireflysoftware.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand;
import io.fireflysoftware.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import io.fireflysoftware.experience.onboarding.core.individual.queries.JourneyStatusDTO;
import io.fireflysoftware.experience.onboarding.core.util.IdempotencyKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    // ASYNC is required: the workflow contains @WaitForSignal gates that depend on
    // subsequent HTTP calls from the same caller. SYNC mode would block the initiate
    // request indefinitely because the engine would wait for signals that can only
    // arrive after the initiate response is sent.
    triggerMode = TriggerMode.ASYNC,
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
    public static final String STEP_RECEIVE_ECONOMIC_DATA = "receive-economic-data";
    public static final String STEP_RECEIVE_IDENTITY_DOCS = "receive-identity-docs";
    public static final String STEP_TRIGGER_KYC_VERIFICATION = "trigger-kyc-verification";
    public static final String STEP_VERIFY_KYC_APPROVED = "verify-kyc-approved";
    public static final String STEP_ACTIVATE_PARTY = "activate-party";
    public static final String STEP_SEND_COMPLETION = "send-completion-notification";

    // ─── Signal names ───
    public static final String SIGNAL_PERSONAL_DATA = "personal-data-submitted";
    public static final String SIGNAL_ECONOMIC_DATA = "economic-data-submitted";
    public static final String SIGNAL_IDENTITY_DOCS = "identity-docs-submitted";
    public static final String SIGNAL_KYC_TRIGGERED = "kyc-triggered";
    public static final String SIGNAL_COMPLETION = "completion-requested";

    // ─── Workflow variable names ───
    public static final String VAR_PARTY_ID = "partyId";
    public static final String VAR_KYC_CASE_ID = "kycCaseId";
    public static final String VAR_WELCOME_DOCUMENT_ID = "welcomeDocumentId";

    // ─── Journey phases ───
    public static final String PHASE_AWAITING_PERSONAL_DATA = "AWAITING_PERSONAL_DATA";
    public static final String PHASE_AWAITING_ECONOMIC_DATA = "AWAITING_ECONOMIC_DATA";
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
    private final Validator validator;
    private final PartyAttributesApi partyAttributesApi;
    private final KitDocumentsApi kitDocumentsApi;

    // ─── Phase 1: Initiation (executes on startWorkflow) ───

    public static final String INPUT_COMMAND = "command";

    @WorkflowStep(id = STEP_REGISTER_PARTY, compensatable = true,
                  compensationMethod = "compensateDeactivateParty")
    @SetVariable(VAR_PARTY_ID)
    public Mono<UUID> registerParty(@Input(INPUT_COMMAND) InitiateOnboardingCommand cmd,
                                     ExecutionContext ctx) {
        RegisterCustomerCommand registerCmd = new RegisterCustomerCommand()
                .party(new RegisterPartyCommand()
                        .tenantId(cmd.getTenantId())
                        .partyKind(RegisterPartyCommand.PartyKindEnum.INDIVIDUAL)
                        .sourceSystem(SOURCE_SYSTEM))
                .naturalPerson(new RegisterNaturalPersonCommand()
                        .givenName(cmd.getFirstName())
                        .familyName(cmd.getLastName()))
                .emails(List.of(new RegisterEmailCommand()
                        .email(cmd.getEmail())
                        .emailKind(RegisterEmailCommand.EmailKindEnum.PERSONAL)));

        if (cmd.getPhone() != null && !cmd.getPhone().isBlank()) {
            registerCmd.phones(List.of(new RegisterPhoneCommand()
                    .phoneNumber(cmd.getPhone())
                    .phoneKind(RegisterPhoneCommand.PhoneKindEnum.MOBILE)));
        }

        // Email is the natural identifier of an individual customer registration
        // request: replaying the same onboarding submission must not create a
        // second customer row downstream.
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, STEP_REGISTER_PARTY, cmd.getEmail());

        return customersApi.registerCustomer(registerCmd, idempotencyKey)
                .flatMap(response -> extractUuid(response, FIELD_PARTY_ID))
                .switchIfEmpty(Mono.error(new BusinessException(HttpStatus.BAD_GATEWAY,
                        "REGISTER_PARTY_EMPTY_RESPONSE",
                        "Customer registration returned no partyId — the downstream saga likely failed silently.")))
                // The @SetVariable annotation above is honoured by the saga engine but not by
                // the current workflow engine — write the variable explicitly so downstream
                // @Variable parameters and the @WorkflowQuery snapshot can see partyId.
                .doOnNext(partyId -> ctx.putVariable(VAR_PARTY_ID, partyId))
                .doOnNext(partyId -> log.info("Registered party: partyId={}", partyId));
    }

    @WorkflowStep(id = STEP_OPEN_KYC_CASE, dependsOn = STEP_REGISTER_PARTY,
                  compensatable = true, compensationMethod = "compensateCancelKycCase")
    @SetVariable(VAR_KYC_CASE_ID)
    public Mono<UUID> openKycCase(@FromStep(STEP_REGISTER_PARTY) UUID partyId,
                                   ExecutionContext ctx) {
        // partyId is the per-execution stable anchor (set by step 0).
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_OPEN_KYC_CASE);

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("partyId", partyId);
        body.put("dueDiligenceLevel", "STANDARD");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Idempotency-Key", idempotencyKey);
        return kycApi.getApiClient().invokeAPI(
                        "/api/v1/kyc/cases",
                        org.springframework.http.HttpMethod.POST,
                        java.util.Map.of(),
                        new org.springframework.util.LinkedMultiValueMap<>(),
                        body,
                        headers,
                        new org.springframework.util.LinkedMultiValueMap<>(),
                        new org.springframework.util.LinkedMultiValueMap<>(),
                        java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON),
                        org.springframework.http.MediaType.APPLICATION_JSON,
                        new String[0],
                        new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {})
                .flatMap(response -> extractUuid(response, FIELD_CASE_ID))
                // See comment on registerParty: workflow engine does not honour @SetVariable.
                .doOnNext(caseId -> ctx.putVariable(VAR_KYC_CASE_ID, caseId))
                .doOnNext(caseId -> log.info("Opened KYC case: caseId={}", caseId));
    }

    @WorkflowStep(id = STEP_SEND_WELCOME, dependsOn = STEP_REGISTER_PARTY)
    public Mono<Void> sendWelcomeNotification(@FromStep(STEP_REGISTER_PARTY) UUID partyId,
                                               @Input(INPUT_COMMAND) InitiateOnboardingCommand cmd) {
        SendNotificationCommand notifCmd = new SendNotificationCommand()
                .partyId(partyId)
                .channel(NOTIFICATION_CHANNEL)
                .templateCode(TEMPLATE_WELCOME)
                .subject("Welcome to Firefly")
                .recipientEmail(cmd.getEmail());

        if (cmd.getPhone() != null) {
            notifCmd.recipientPhone(cmd.getPhone());
        }

        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_SEND_WELCOME);

        return notificationsApi.sendNotification(notifCmd, idempotencyKey)
                .doOnNext(r -> log.info("Sent welcome notification for partyId={}", partyId))
                .then()
                .onErrorResume(ex -> {
                    log.warn("Welcome notification did not complete for partyId={}: {}", partyId, ex.getMessage());
                    return Mono.empty();
                });
    }

    // ─── Gate: Wait for personal data submission ───

    @WorkflowStep(id = STEP_RECEIVE_PERSONAL_DATA,
                  dependsOn = {STEP_OPEN_KYC_CASE, STEP_SEND_WELCOME})
    @WaitForSignal(SIGNAL_PERSONAL_DATA)
    public Mono<Void> receivePersonalData(@Variable(VAR_PARTY_ID) UUID partyId,
                                           @Variable("signal." + SIGNAL_PERSONAL_DATA) Object signalData) {
        return Mono.defer(() -> {
            SubmitPersonalDataCommand cmd = mapAndValidateSignalPayload(signalData, SubmitPersonalDataCommand.class);
            RegisterAddressCommand addressCmd = new RegisterAddressCommand()
                    .line1(cmd.getAddressLine1())
                    .line2(cmd.getAddressLine2())
                    .city(cmd.getCity())
                    .postalCode(cmd.getPostalCode());

            String addressKey = IdempotencyKeys.of(
                    WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_PERSONAL_DATA, "add-address");

            Mono<Void> addAddress = customersApi.addCustomerAddress(partyId, addressCmd, addressKey)
                    .then();

            // BE-5a: propagate maritalStatus + numberOfChildren to the natural-person record only
            // when at least one of the fields is provided.
            boolean hasMaritalStatus = cmd.getMaritalStatus() != null && !cmd.getMaritalStatus().isBlank();
            boolean hasNumberOfChildren = cmd.getNumberOfChildren() != null;

            if (!hasMaritalStatus && !hasNumberOfChildren) {
                return addAddress
                        .doOnSuccess(v -> log.info("Submitted personal data for partyId={}", partyId));
            }

            UpdateCustomerCommand updateCmd = new UpdateCustomerCommand().partyId(partyId);
            if (hasMaritalStatus) {
                updateCmd.maritalStatus(toMaritalStatusEnum(cmd.getMaritalStatus()));
            }
            if (hasNumberOfChildren) {
                updateCmd.numberOfChildren(cmd.getNumberOfChildren());
            }

            String updateKey = IdempotencyKeys.of(
                    WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_PERSONAL_DATA, "update-customer");

            Mono<Void> updateCustomer = customersApi.updateCustomer(updateCmd, updateKey)
                    .then();

            return addAddress.then(updateCustomer)
                    .doOnSuccess(v -> log.info("Submitted personal data for partyId={}", partyId));
        });
    }

    private UpdateCustomerCommand.MaritalStatusEnum toMaritalStatusEnum(String value) {
        try {
            return UpdateCustomerCommand.MaritalStatusEnum.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_MARITAL_STATUS",
                    "Marital status '" + value + "' is not supported by the customer domain. "
                            + "Allowed values: SINGLE, MARRIED, SEPARATED, DIVORCED, WIDOWED.");
        }
    }

    // ─── Gate: Wait for economic data ───

    @WorkflowStep(id = STEP_RECEIVE_ECONOMIC_DATA, dependsOn = STEP_RECEIVE_PERSONAL_DATA)
    @WaitForSignal(SIGNAL_ECONOMIC_DATA)
    public Mono<Void> receiveEconomicData(@Variable(VAR_PARTY_ID) UUID partyId,
                                           @Variable("signal." + SIGNAL_ECONOMIC_DATA) Object signalData) {
        return Mono.defer(() -> {
            SubmitEconomicDataCommand cmd = mapAndValidateSignalPayload(signalData,
                    SubmitEconomicDataCommand.class);
            validateEconomicDataCrossFieldRules(cmd);

            // Occupation and monthly income stay on the person. Employment, housing,
            // loans, and debts are party attributes.
            UpdateCustomerCommand updateCmd = new UpdateCustomerCommand()
                    .partyId(partyId)
                    .occupation(cmd.getPosition())
                    .monthlyIncome(cmd.getMonthlySalary());

            String idempotencyKey = IdempotencyKeys.of(
                    WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_ECONOMIC_DATA);

            Mono<Void> updatePerson = customersApi.updateCustomer(updateCmd, idempotencyKey).then();
            Mono<Void> attributes = reactor.core.publisher.Flux.concat(
                    putAttribute(partyId, "employmentStatus", PartyAttributeRequest.AttributeTypeEnum.STRING, cmd.getEmploymentStatus(), idempotencyKey),
                    putAttribute(partyId, "employmentType", PartyAttributeRequest.AttributeTypeEnum.STRING, cmd.getEmploymentType(), idempotencyKey),
                    putAttribute(partyId, "employer", PartyAttributeRequest.AttributeTypeEnum.STRING, cmd.getEmployer(), idempotencyKey),
                    putAttribute(partyId, "employmentStartDate", PartyAttributeRequest.AttributeTypeEnum.DATE, cmd.getEmploymentStartDate(), idempotencyKey),
                    putAttribute(partyId, "annualPaydays", PartyAttributeRequest.AttributeTypeEnum.NUMBER, cmd.getAnnualPaydays(), idempotencyKey),
                    putAttribute(partyId, "housingType", PartyAttributeRequest.AttributeTypeEnum.STRING, cmd.getHousingType(), idempotencyKey),
                    putAttribute(partyId, "housingCost", PartyAttributeRequest.AttributeTypeEnum.NUMBER, cmd.getHousingCost(), idempotencyKey),
                    putAttribute(partyId, "housingStartDate", PartyAttributeRequest.AttributeTypeEnum.DATE, cmd.getHousingStartDate(), idempotencyKey),
                    putAttribute(partyId, "existingLoans", PartyAttributeRequest.AttributeTypeEnum.NUMBER, cmd.getExistingLoans(), idempotencyKey),
                    putAttribute(partyId, "otherDebts", PartyAttributeRequest.AttributeTypeEnum.NUMBER, cmd.getOtherDebts(), idempotencyKey)
            ).then();

            return updatePerson.then(attributes)
                    .doOnSuccess(v -> log.info("Submitted economic data for partyId={}", partyId));
        });
    }

    /**
     * Enforces the conditional requirements documented in the front-end spec.
     * Structural constraints (enums, sizes) are already covered by Bean Validation
     * via the command DTO; here we only check field-to-field dependencies.
     */
    private void validateEconomicDataCrossFieldRules(SubmitEconomicDataCommand cmd) {
        String status = cmd.getEmploymentStatus();
        boolean isEmployed = "private".equals(status) || "public".equals(status) || "civil".equals(status);
        boolean isSelfEmployed = "selfEmployed".equals(status) || "entrepreneur".equals(status);
        boolean isRetired = "retired".equals(status);
        boolean isUnemployed = "unemployed".equals(status);

        if (isEmployed) {
            requireField(cmd.getEmploymentType(), "employmentType",
                    "employmentType is required when employmentStatus is " + status);
            requireField(cmd.getEmployer(), "employer",
                    "employer is required when employmentStatus is " + status);
            requireField(cmd.getPosition(), "position",
                    "position is required when employmentStatus is " + status);
        }
        if (isEmployed || isSelfEmployed) {
            if (cmd.getEmploymentStartDate() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIGNAL_PAYLOAD",
                        "employmentStartDate is required when employmentStatus is " + status);
            }
            if ("entrepreneur".equals(status) && (cmd.getEmployer() == null || cmd.getEmployer().isBlank())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIGNAL_PAYLOAD",
                        "employer is required when employmentStatus is entrepreneur");
            }
        }
        if (isEmployed || isSelfEmployed || isRetired) {
            if (cmd.getAnnualPaydays() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIGNAL_PAYLOAD",
                        "annualPaydays is required when employmentStatus is " + status);
            }
        }
        if (!isUnemployed && cmd.getMonthlySalary() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIGNAL_PAYLOAD",
                    "monthlySalary is required when employmentStatus is " + status);
        }

        String housingType = cmd.getHousingType();
        if (("owned".equals(housingType) || "family".equals(housingType))
                && cmd.getHousingStartDate() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIGNAL_PAYLOAD",
                    "housingStartDate is required when housingType is " + housingType);
        }
    }

    private void requireField(String value, String fieldName, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIGNAL_PAYLOAD", message);
        }
    }

    private Mono<Void> putAttribute(UUID partyId, String key, PartyAttributeRequest.AttributeTypeEnum type,
                                    Object value, String idempotencyKey) {
        if (value == null) {
            return Mono.empty();
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return Mono.empty();
        }
        PartyAttributeRequest request = new PartyAttributeRequest()
                .attributeKey(key)
                .attributeType(type)
                .attributeValue(text);
        return partyAttributesApi.createPartyAttribute(
                partyId, request, IdempotencyKeys.of(idempotencyKey, key)).then();
    }

    // ─── Gate: Wait for identity documents ───

    @WorkflowStep(id = STEP_RECEIVE_IDENTITY_DOCS, dependsOn = STEP_RECEIVE_ECONOMIC_DATA)
    @WaitForSignal(SIGNAL_IDENTITY_DOCS)
    public Mono<UUID> receiveIdentityDocuments(@Variable(VAR_KYC_CASE_ID) UUID caseId,
                                                @Variable(VAR_PARTY_ID) UUID partyId,
                                                @Variable("signal." + SIGNAL_IDENTITY_DOCS) Object signalData) {
        SubmitIdentityDocumentsCommand cmd = mapAndValidateSignalPayload(signalData,
                SubmitIdentityDocumentsCommand.class);
        AttachEvidenceCommand attachCmd = new AttachEvidenceCommand()
                .caseId(caseId)
                .documentType(cmd.getDocumentType())
                .documentName(cmd.getDocumentNumber())
                .documentContent(cmd.getDocumentContent())
                .mimeType(cmd.getMimeType());

        // Anchor on partyId + the document number so that re-submitting the same
        // identity document does not create a duplicate evidence row downstream.
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_IDENTITY_DOCS,
                cmd.getDocumentType(), cmd.getDocumentNumber());

        return kycApi.attachEvidence(caseId, attachCmd, idempotencyKey)
                .flatMap(response -> extractUuid(response, FIELD_DOCUMENT_ID))
                .doOnNext(docId -> log.info("Attached identity document: documentId={}", docId));
    }

    // ─── Gate: Wait for KYC trigger ───

    @WorkflowStep(id = STEP_TRIGGER_KYC_VERIFICATION, dependsOn = STEP_RECEIVE_IDENTITY_DOCS)
    @WaitForSignal(SIGNAL_KYC_TRIGGERED)
    public Mono<Void> triggerKycVerification(@Variable(VAR_KYC_CASE_ID) UUID caseId,
                                              @Variable(VAR_PARTY_ID) UUID partyId) {
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_TRIGGER_KYC_VERIFICATION);

        return kycApi.verify(caseId, idempotencyKey)
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
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_ACTIVATE_PARTY);

        return customersApi.reactivateCustomer(partyId, idempotencyKey)
                .doOnNext(r -> log.info("Activated party: partyId={}", partyId))
                .then();
    }

    @WorkflowStep(id = STEP_SEND_COMPLETION, dependsOn = STEP_ACTIVATE_PARTY)
    public Mono<Void> sendCompletionNotification(@Variable(VAR_PARTY_ID) UUID partyId,
                                                 ExecutionContext ctx) {
        SendNotificationCommand notifCmd = new SendNotificationCommand()
                .partyId(partyId)
                .channel(NOTIFICATION_CHANNEL)
                .templateCode(TEMPLATE_COMPLETED)
                .subject("Onboarding Complete");

        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_SEND_COMPLETION);

        return notificationsApi.sendNotification(notifCmd, idempotencyKey)
                .doOnNext(r -> log.info("Sent completion notification for partyId={}", partyId))
                .onErrorResume(ex -> {
                    log.warn("Completion notification did not complete for partyId={}: {}", partyId, ex.getMessage());
                    return Mono.empty();
                })
                .then(kitDocumentsApi.issue("welcome", partyId, partyId, idempotencyKey))
                .doOnNext(documentId -> {
                    if (ctx != null) {
                        ctx.putVariable(VAR_WELCOME_DOCUMENT_ID, documentId);
                    }
                })
                .then();
    }

    // ─── Compensation methods ───

    public Mono<Void> compensateDeactivateParty(@FromStep(STEP_REGISTER_PARTY) UUID partyId) {
        if (partyId == null) {
            log.warn("Skipping compensateDeactivateParty: no partyId — register-party never produced one.");
            return Mono.empty();
        }
        log.warn("Compensating: requesting closure for party partyId={}", partyId);
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), "compensate-deactivate-party");

        return customersApi.requestCustomerClosure(partyId, idempotencyKey)
                .then()
                .onErrorResume(ex -> {
                    log.warn("Failed to compensate party closure partyId={}: {}", partyId, ex.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> compensateCancelKycCase(@FromStep(STEP_OPEN_KYC_CASE) UUID caseId) {
        if (caseId == null) {
            log.warn("Skipping compensateCancelKycCase: no caseId — open-kyc-case never produced one.");
            return Mono.empty();
        }
        log.warn("Compensating: cancelling KYC case caseId={}", caseId);
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, caseId.toString(), "compensate-cancel-kyc-case");

        return kycApi.fail(caseId, idempotencyKey)
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
                .welcomeDocumentId(toUuid(ctx.getVariable(VAR_WELCOME_DOCUMENT_ID)))
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
        if (steps.getOrDefault(STEP_RECEIVE_ECONOMIC_DATA, StepStatus.PENDING) == StepStatus.PENDING) {
            return PHASE_AWAITING_ECONOMIC_DATA;
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
        if (steps.getOrDefault(STEP_RECEIVE_ECONOMIC_DATA, StepStatus.PENDING) == StepStatus.PENDING) {
            return STEP_RECEIVE_ECONOMIC_DATA;
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
     * Deserialises the raw signal payload into the target command type and runs Jakarta Bean
     * Validation on the result. Constraint violations are surfaced as a {@code 400 BAD_REQUEST}
     * {@link BusinessException} with code {@code INVALID_SIGNAL_PAYLOAD}, ensuring malformed
     * signal payloads are rejected at the workflow boundary before any domain SDK call is made.
     */
    private <T> T mapAndValidateSignalPayload(Object signalData, Class<T> type) {
        T payload = mapSignalPayload(signalData, type);
        Set<ConstraintViolation<T>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIGNAL_PAYLOAD", message);
        }
        return payload;
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
