package com.firefly.experience.onboarding.core.business.workflows;

import com.firefly.domain.common.notifications.sdk.api.NotificationsApi;
import com.firefly.domain.common.notifications.sdk.model.SendNotificationCommand;
import com.firefly.domain.kyc.kyb.sdk.api.KybApi;
import com.firefly.domain.kyc.kyb.sdk.model.CreateKybCaseRequest;
import com.firefly.domain.kyc.kyb.sdk.model.DocumentData;
import com.firefly.domain.kyc.kyb.sdk.model.RegisterUbosRequest;
import com.firefly.domain.kyc.kyb.sdk.model.SignerData;
import com.firefly.domain.kyc.kyb.sdk.model.SubmitAuthorizedSignersRequest;
import com.firefly.domain.kyc.kyb.sdk.model.SubmitCorporateDocumentsRequest;
import com.firefly.domain.kyc.kyb.sdk.model.UboData;
import com.firefly.domain.people.sdk.api.BusinessesApi;
import com.firefly.domain.people.sdk.api.CustomersApi;
import com.firefly.domain.people.sdk.model.RegisterAddressCommand;
import com.firefly.domain.people.sdk.model.RegisterBusinessCommand;
import com.firefly.domain.people.sdk.model.RegisterCustomerCommand;
import com.firefly.domain.people.sdk.model.RegisterLegalEntityCommand;
import com.firefly.domain.people.sdk.model.RegisterNaturalPersonCommand;
import com.firefly.domain.people.sdk.model.RegisterPartyCommand;
import com.firefly.domain.people.sdk.model.UpdateBusinessCommand;
import com.firefly.experience.onboarding.core.business.commands.InitiateBusinessOnboardingCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCorporateDocumentsCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitUbosCommand;
import com.firefly.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
import com.firefly.experience.onboarding.core.util.IdempotencyKeys;
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
    public static final String VAR_BUSINESS_NAME = "businessName";
    public static final String VAR_REGISTRATION_NUMBER = "registrationNumber";
    public static final String VAR_TENANT_ID = "tenantId";

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
    private final CustomersApi customersApi;
    private final KybApi kybApi;
    private final NotificationsApi notificationsApi;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    // ─── Phase 1: Initiation (executes on startWorkflow) ───

    public static final String INPUT_COMMAND = "command";

    @WorkflowStep(id = STEP_REGISTER_PARTY, compensatable = true,
                  compensationMethod = "compensateDeactivateParty")
    @SetVariable(VAR_PARTY_ID)
    public Mono<UUID> registerBusinessParty(@Input(INPUT_COMMAND) InitiateBusinessOnboardingCommand cmd,
                                             ExecutionContext ctx) {
        ctx.putVariable(VAR_BUSINESS_NAME, cmd.getBusinessName());
        ctx.putVariable(VAR_REGISTRATION_NUMBER, cmd.getRegistrationNumber());
        if (cmd.getTenantId() != null) {
            ctx.putVariable(VAR_TENANT_ID, cmd.getTenantId());
        }

        RegisterBusinessCommand registerCmd = new RegisterBusinessCommand()
                .party(new RegisterPartyCommand()
                        .tenantId(cmd.getTenantId())
                        .partyKind(RegisterPartyCommand.PartyKindEnum.ORGANIZATION)
                        .sourceSystem(SOURCE_SYSTEM))
                .legalEntity(new RegisterLegalEntityCommand()
                        .legalName(cmd.getBusinessName())
                        .registrationNumber(cmd.getRegistrationNumber()));

        // Deterministic key: a workflow replay of the same execution must produce
        // the same key so the upstream registration is not duplicated. The
        // correlation id is the per-execution stable anchor.
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, ctx.getCorrelationId(), STEP_REGISTER_PARTY);

        return businessesApi.registerBusiness(registerCmd, idempotencyKey)
                .flatMap(response -> extractUuid(response, FIELD_PARTY_ID))
                .switchIfEmpty(Mono.error(new BusinessException(HttpStatus.BAD_GATEWAY,
                        "REGISTER_PARTY_EMPTY_RESPONSE",
                        "Business registration returned no partyId — the downstream saga likely failed silently.")))
                .doOnNext(partyId -> ctx.putVariable(VAR_PARTY_ID, partyId))
                .doOnNext(partyId -> log.info("Registered business party: partyId={}", partyId));
    }

    @WorkflowStep(id = STEP_OPEN_KYB_CASE, dependsOn = STEP_REGISTER_PARTY,
                  compensatable = true, compensationMethod = "compensateCancelKybCase")
    @SetVariable(VAR_KYB_CASE_ID)
    public Mono<UUID> openKybCase(@FromStep(STEP_REGISTER_PARTY) UUID partyId,
                                   ExecutionContext ctx) {
        String businessName = ctx.getVariableAs(VAR_BUSINESS_NAME, String.class);
        String registrationNumber = ctx.getVariableAs(VAR_REGISTRATION_NUMBER, String.class);
        UUID tenantId = ctx.getVariableAs(VAR_TENANT_ID, UUID.class);

        CreateKybCaseRequest request = new CreateKybCaseRequest()
                .tenantId(tenantId)
                .partyId(partyId)
                .businessName(businessName)
                .registrationNumber(registrationNumber);

        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, ctx.getCorrelationId(), STEP_OPEN_KYB_CASE);

        return kybApi.createCase(request, idempotencyKey)
                .map(response -> response.getCaseId())
                .doOnNext(caseId -> ctx.putVariable(VAR_KYB_CASE_ID, caseId))
                .doOnNext(caseId -> log.info("Opened KYB case: caseId={}", caseId));
    }

    @WorkflowStep(id = STEP_SEND_WELCOME, dependsOn = STEP_REGISTER_PARTY)
    public Mono<Void> sendWelcomeNotification(@FromStep(STEP_REGISTER_PARTY) UUID partyId,
                                               @Input(INPUT_COMMAND) InitiateBusinessOnboardingCommand cmd) {
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

        // partyId is generated in step 0 and is the per-execution stable anchor
        // for any notification/op tied to this onboarding journey.
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_SEND_WELCOME);

        return notificationsApi.sendNotification(notifCmd, idempotencyKey)
                .doOnNext(r -> log.info("Sent business welcome notification for partyId={}", partyId))
                .then();
    }

    // ─── Gate: Wait for company data submission ───

    @WorkflowStep(id = STEP_RECEIVE_COMPANY_DATA,
                  dependsOn = {STEP_OPEN_KYB_CASE, STEP_SEND_WELCOME})
    @WaitForSignal(SIGNAL_COMPANY_DATA)
    public Mono<Void> receiveCompanyData(@Variable(VAR_PARTY_ID) UUID partyId,
                                          Object signalData) {
        SubmitCompanyDataCommand cmd = mapAndValidateSignalPayload(signalData, SubmitCompanyDataCommand.class);

        // Update the legal entity with full company details via domain SDK.
        // BE-5b: propagate the new corporate fields (employeeRange, annualRevenue,
        // cnaeCode, contactName, contactPosition, contactEmail, contactPhone).
        UpdateBusinessCommand updateCmd = new UpdateBusinessCommand()
                .partyId(partyId)
                .legalName(cmd.getLegalName())
                .tradeName(cmd.getTradeName())
                .incorporationDate(cmd.getIncorporationDate())
                .taxIdNumber(cmd.getTaxId())
                .industryDescription(cmd.getBusinessActivity())
                .employeeRange(cmd.getNumberOfEmployees())
                .annualRevenue(cmd.getAnnualRevenue())
                .cnaeCode(cmd.getCnaeCode())
                .contactName(cmd.getContactName())
                .contactPosition(cmd.getContactPosition())
                .contactEmail(cmd.getContactEmail())
                .contactPhone(cmd.getContactPhone());

        // partyId is unique per workflow execution (minted in step 0). Combined
        // with a per-call discriminator it is a stable idempotency anchor across
        // signal-driven step replays.
        String updateKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_COMPANY_DATA, "update-business");
        String addressKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_COMPANY_DATA, "add-address");

        Mono<Void> updateLegalEntity = businessesApi.updateBusiness(updateCmd, updateKey)
                .then();

        // Register business address
        RegisterAddressCommand addressCmd = new RegisterAddressCommand()
                .line1(cmd.getAddressLine1())
                .line2(cmd.getAddressLine2())
                .city(cmd.getCity())
                .postalCode(cmd.getPostalCode());

        Mono<Void> addAddress = businessesApi.addBusinessAddress(partyId, addressCmd, addressKey)
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
        SubmitUbosCommand cmd = mapAndValidateSignalPayload(signalData, SubmitUbosCommand.class);

        if (cmd.getUbos() == null || cmd.getUbos().isEmpty()) {
            log.info("No UBOs submitted for partyId={}, caseId={} — skipping", partyId, caseId);
            return Mono.empty();
        }

        // BE-5d: each UBO is a natural person and the core schema requires a non-null
        // naturalPersonId on the UBO row. Register each UBO via the customer service
        // first, then build UboData with the resolved naturalPersonId before submitting
        // the ownership records to the KYB saga. Email and ownershipType from the front
        // are propagated; ownershipType defaults to DIRECT when not provided.
        return reactor.core.publisher.Flux.fromIterable(cmd.getUbos())
                .concatMap(ubo -> resolveUboNaturalPersonId(ubo, partyId))
                .collectList()
                .flatMap(uboDataList -> {
                    RegisterUbosRequest request = new RegisterUbosRequest()
                            .partyId(partyId)
                            .ubos(uboDataList);

                    String idempotencyKey = IdempotencyKeys.of(
                            WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_UBOS);

                    return kybApi.registerUbos(caseId, request, idempotencyKey)
                            .doOnNext(r -> log.info("Registered {} UBOs for partyId={}, caseId={}",
                                    uboDataList.size(), partyId, caseId))
                            .then();
                });
    }

    /**
     * Registers the UBO as a natural person via {@link CustomersApi#registerCustomer}
     * and returns a {@link UboData} populated with the resolved party ID as
     * {@code naturalPersonId}. The UBO's identity fields (firstName, lastName,
     * documentNumber) are propagated to the natural-person record so the customer row
     * carries a document-traceable identity (SEPBLAC requirement). The BE-5d
     * ownership fields (ownershipPercentage, ownershipType, email) are propagated
     * to the UBO record.
     */
    private Mono<UboData> resolveUboNaturalPersonId(SubmitUbosCommand.UboEntry ubo, UUID partyId) {
        RegisterCustomerCommand registerCmd = new RegisterCustomerCommand()
                .party(new RegisterPartyCommand()
                        .partyKind(RegisterPartyCommand.PartyKindEnum.INDIVIDUAL)
                        .sourceSystem(SOURCE_SYSTEM))
                .naturalPerson(new RegisterNaturalPersonCommand()
                        .givenName(ubo.getFirstName())
                        .familyName1(ubo.getLastName())
                        .taxIdNumber(ubo.getDocumentNumber()));

        // Per-UBO deterministic key: same partyId + same UBO documentNumber must
        // resolve to the same natural-person registration on every replay so the
        // customer service deduplicates the row.
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_UBOS,
                "register-ubo-natural-person", ubo.getDocumentNumber());

        return customersApi.registerCustomer(registerCmd, idempotencyKey)
                .flatMap(response -> extractUuid(response, FIELD_PARTY_ID))
                .doOnNext(naturalPersonId -> log.info("Registered UBO natural person: naturalPersonId={}", naturalPersonId))
                .map(naturalPersonId -> new UboData()
                        .naturalPersonId(naturalPersonId)
                        .ownershipPercentage(ubo.getOwnershipPercentage())
                        .ownershipType(normaliseOwnershipType(ubo.getOwnershipType()))
                        .email(ubo.getEmail()));
    }

    // ─── Gate: Wait for corporate documents ───

    @WorkflowStep(id = STEP_RECEIVE_CORPORATE_DOCS, dependsOn = STEP_RECEIVE_UBOS)
    @WaitForSignal(SIGNAL_CORPORATE_DOCS)
    public Mono<Void> receiveCorporateDocuments(@Variable(VAR_KYB_CASE_ID) UUID caseId,
                                                 @Variable(VAR_PARTY_ID) UUID partyId,
                                                 Object signalData) {
        SubmitCorporateDocumentsCommand cmd = mapAndValidateSignalPayload(signalData, SubmitCorporateDocumentsCommand.class);

        if (cmd.getDocuments() == null || cmd.getDocuments().isEmpty()) {
            log.info("No corporate documents submitted for caseId={} — skipping", caseId);
            return Mono.empty();
        }

        List<DocumentData> documentDataList = cmd.getDocuments().stream()
                .map(doc -> new DocumentData()
                        .documentType(doc.getDocumentType())
                        .documentReference(doc.getDocumentReference()))
                .toList();

        SubmitCorporateDocumentsRequest request = new SubmitCorporateDocumentsRequest()
                .partyId(partyId)
                .documents(documentDataList);

        // partyId is the per-execution stable anchor; the step id discriminates
        // this submission from other operations against the same case.
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_CORPORATE_DOCS);

        return kybApi.submitCorporateDocuments(caseId, request, idempotencyKey)
                .doOnNext(r -> log.info("Submitted {} corporate documents for caseId={}",
                        documentDataList.size(), caseId))
                .then();
    }

    // ─── Gate: Wait for authorized signers ───

    @WorkflowStep(id = STEP_RECEIVE_AUTHORIZED_SIGNERS, dependsOn = STEP_RECEIVE_CORPORATE_DOCS)
    @WaitForSignal(SIGNAL_AUTHORIZED_SIGNERS)
    public Mono<Void> receiveAuthorizedSigners(@Variable(VAR_PARTY_ID) UUID partyId,
                                                @Variable(VAR_KYB_CASE_ID) UUID caseId,
                                                Object signalData) {
        SubmitAuthorizedSignersCommand cmd = mapAndValidateSignalPayload(signalData, SubmitAuthorizedSignersCommand.class);

        if (cmd.getSigners() == null || cmd.getSigners().isEmpty()) {
            log.info("No authorized signers submitted for partyId={}, caseId={} — skipping", partyId, caseId);
            return Mono.empty();
        }

        // BE-5c: each authorized signer is a natural person. Register them via the
        // customer service first, then build SignerData with the resolved attorneyId
        // before submitting the powers of attorney to the KYB saga.
        return reactor.core.publisher.Flux.fromIterable(cmd.getSigners())
                .concatMap(signer -> resolveSignerAttorneyId(signer, partyId))
                .collectList()
                .flatMap(signerDataList -> {
                    SubmitAuthorizedSignersRequest request = new SubmitAuthorizedSignersRequest()
                            .partyId(partyId)
                            .signers(signerDataList);

                    String idempotencyKey = IdempotencyKeys.of(
                            WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_AUTHORIZED_SIGNERS);

                    return kybApi.submitAuthorizedSigners(caseId, request, idempotencyKey)
                            .doOnNext(r -> log.info("Registered {} authorized signers for partyId={}, caseId={}",
                                    signerDataList.size(), partyId, caseId))
                            .then();
                });
    }

    /**
     * Registers the signer as a natural person via {@link CustomersApi#registerCustomer}
     * and returns a {@link SignerData} populated with the resolved party ID as
     * {@code attorneyId}. The signer's identity fields (firstName, lastName, documentNumber)
     * are propagated to the natural-person record so the customer row carries a
     * document-traceable identity (SEPBLAC requirement). The full document type and
     * number remain on the {@link SignerData} power-of-attorney record. The BE-5c
     * contact fields (role, email, signingAuthorized, isPep) are propagated to the
     * power of attorney.
     */
    private Mono<SignerData> resolveSignerAttorneyId(
            com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand.SignerEntry signer,
            UUID partyId) {
        RegisterCustomerCommand registerCmd = new RegisterCustomerCommand()
                .party(new RegisterPartyCommand()
                        .partyKind(RegisterPartyCommand.PartyKindEnum.INDIVIDUAL)
                        .sourceSystem(SOURCE_SYSTEM))
                .naturalPerson(new RegisterNaturalPersonCommand()
                        .givenName(signer.getFirstName())
                        .familyName1(signer.getLastName())
                        .taxIdNumber(signer.getDocumentNumber()));

        // Per-signer deterministic key: same partyId + same signer documentNumber
        // must resolve to the same natural-person registration on every replay.
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_RECEIVE_AUTHORIZED_SIGNERS,
                "register-signer-natural-person", signer.getDocumentNumber());

        return customersApi.registerCustomer(registerCmd, idempotencyKey)
                .flatMap(response -> extractUuid(response, FIELD_PARTY_ID))
                .doOnNext(attorneyId -> log.info("Registered attorney natural person: attorneyId={}", attorneyId))
                .map(attorneyId -> new SignerData()
                        .firstName(signer.getFirstName())
                        .lastName(signer.getLastName())
                        .documentType(signer.getDocumentType())
                        .documentNumber(signer.getDocumentNumber())
                        .attorneyId(attorneyId)
                        .role(signer.getRole())
                        .powerDocumentReference(signer.getPowerDocumentReference())
                        .email(signer.getEmail())
                        .signingAuthorized(signer.getSigningAuthorized())
                        .isPep(signer.getIsPep()));
    }

    private String normaliseOwnershipType(String ownershipType) {
        if (ownershipType == null || ownershipType.isBlank()) {
            return "DIRECT";
        }
        return ownershipType.trim().toUpperCase();
    }

    // ─── Gate: Wait for KYB verification trigger ───

    @WorkflowStep(id = STEP_TRIGGER_KYB, dependsOn = STEP_RECEIVE_AUTHORIZED_SIGNERS)
    @WaitForSignal(SIGNAL_KYB_TRIGGERED)
    public Mono<Void> triggerKybVerification(@Variable(VAR_KYB_CASE_ID) UUID caseId,
                                              @Variable(VAR_PARTY_ID) UUID partyId) {
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_TRIGGER_KYB);

        return kybApi.requestVerification(caseId, partyId, idempotencyKey)
                .doOnNext(r -> log.info("Triggered KYB verification for caseId={}", caseId))
                .then();
    }

    // ─── Gate: Wait for completion request ───

    @WorkflowStep(id = STEP_VERIFY_KYB_APPROVED, dependsOn = STEP_TRIGGER_KYB)
    @WaitForSignal(SIGNAL_COMPLETION)
    public Mono<Void> verifyKybApproved(@Variable(VAR_KYB_CASE_ID) UUID caseId) {
        // getCase is read-only but we pass a deterministic key so the
        // downstream service can reuse cached results across retries.
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, caseId.toString(), STEP_VERIFY_KYB_APPROVED);

        return kybApi.getCase(caseId, idempotencyKey)
                .flatMap(response -> {
                    String status = response.getCaseStatus();
                    if ("VERIFIED".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status)
                            || "CLOSED".equalsIgnoreCase(status)) {
                        log.info("KYB case approved for caseId={}, status={}", caseId, status);
                        return Mono.<Void>empty();
                    }
                    return Mono.<Void>error(new BusinessException(
                            HttpStatus.CONFLICT, "KYB_NOT_VERIFIED",
                            "KYB not yet verified. Current status: " + status));
                });
    }

    @WorkflowStep(id = STEP_ACTIVATE_PARTY, dependsOn = STEP_VERIFY_KYB_APPROVED)
    public Mono<Void> activateBusinessParty(@Variable(VAR_PARTY_ID) UUID partyId) {
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_ACTIVATE_PARTY);

        return businessesApi.reactivateBusiness(partyId, idempotencyKey)
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

        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), STEP_SEND_COMPLETION);

        return notificationsApi.sendNotification(notifCmd, idempotencyKey)
                .doOnNext(r -> log.info("Sent business completion notification for partyId={}", partyId))
                .then();
    }

    // ─── Compensation methods ───

    public Mono<Void> compensateDeactivateParty(@FromStep(STEP_REGISTER_PARTY) UUID partyId) {
        if (partyId == null) {
            log.warn("Compensating: skipping party closure — register-business-party did not produce a partyId");
            return Mono.empty();
        }
        log.warn("Compensating: requesting closure for business party partyId={}", partyId);
        String idempotencyKey = IdempotencyKeys.of(
                WORKFLOW_ID, partyId.toString(), "compensate-deactivate-party");

        return businessesApi.requestBusinessClosure(partyId, idempotencyKey)
                .then()
                .onErrorResume(ex -> {
                    log.warn("Failed to compensate business party closure partyId={}: {}", partyId, ex.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> compensateCancelKybCase(@FromStep(STEP_OPEN_KYB_CASE) UUID caseId) {
        // KYB SDK does not expose a cancel/fail endpoint — log the orphaned case for manual follow-up.
        log.warn("Compensating: KYB case caseId={} requires manual cancellation — no cancel endpoint in SDK", caseId);
        return Mono.empty();
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

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String s) return UUID.fromString(s);
        return null;
    }
}
