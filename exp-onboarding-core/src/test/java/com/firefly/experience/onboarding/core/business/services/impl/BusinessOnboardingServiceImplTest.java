package com.firefly.experience.onboarding.core.business.services.impl;

import com.firefly.experience.onboarding.core.business.commands.InitiateBusinessOnboardingCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCorporateDocumentsCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitUbosCommand;
import com.firefly.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
import com.firefly.experience.onboarding.core.business.queries.KybStatusDTO;
import com.firefly.experience.onboarding.core.business.workflows.BusinessOnboardingWorkflow;
import com.firefly.domain.people.sdk.api.BusinessesApi;
import org.fireflyframework.orchestration.core.persistence.ExecutionState;
import org.fireflyframework.orchestration.workflow.engine.WorkflowEngine;
import org.fireflyframework.orchestration.workflow.query.WorkflowQueryService;
import org.fireflyframework.orchestration.workflow.signal.SignalResult;
import org.fireflyframework.orchestration.workflow.signal.SignalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessOnboardingServiceImplTest {

    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private SignalService signalService;
    @Mock
    private WorkflowQueryService queryService;
    @Mock
    private BusinessesApi businessesApi;
    @Mock
    private ExecutionState executionState;

    private BusinessOnboardingServiceImpl service;

    private static final UUID TEST_ONBOARDING_ID = UUID.randomUUID();

    private static final BusinessOnboardingStatusDTO MOCK_STATUS = BusinessOnboardingStatusDTO.builder()
            .onboardingId(TEST_ONBOARDING_ID)
            .partyId(UUID.randomUUID())
            .kybCaseId(UUID.randomUUID())
            .currentPhase(BusinessOnboardingWorkflow.PHASE_INITIATED)
            .completedSteps(List.of(
                    BusinessOnboardingWorkflow.STEP_REGISTER_PARTY,
                    BusinessOnboardingWorkflow.STEP_OPEN_KYB_CASE,
                    BusinessOnboardingWorkflow.STEP_SEND_WELCOME))
            .nextStep(BusinessOnboardingWorkflow.STEP_RECEIVE_COMPANY_DATA)
            .kybStatus("CASE_OPENED")
            .build();

    @BeforeEach
    void setUp() {
        service = new BusinessOnboardingServiceImpl(workflowEngine, signalService, queryService, businessesApi);
    }

    @Test
    void initiateOnboarding_startsWorkflowAndReturnsStatus() {
        when(workflowEngine.startWorkflow(
                eq(BusinessOnboardingWorkflow.WORKFLOW_ID), any(Map.class), anyString(), eq("api"), eq(false)))
                .thenReturn(Mono.just(executionState));
        when(queryService.executeQuery(anyString(), eq(BusinessOnboardingWorkflow.QUERY_JOURNEY_STATUS)))
                .thenReturn(Mono.just(MOCK_STATUS));

        InitiateBusinessOnboardingCommand cmd = InitiateBusinessOnboardingCommand.builder()
                .businessName("Acme Corp")
                .registrationNumber("B12345678")
                .countryOfIncorporation("ES")
                .build();

        StepVerifier.create(service.initiateOnboarding(cmd))
                .assertNext(status -> {
                    assertThat(status.getCurrentPhase()).isEqualTo(BusinessOnboardingWorkflow.PHASE_INITIATED);
                    assertThat(status.getPartyId()).isNotNull();
                })
                .verifyComplete();

        // Asserts QA Issue 2 fix: the workflow input map keys the command under
        // INPUT_COMMAND so the @Input(INPUT_COMMAND) resolver can extract it.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(workflowEngine).startWorkflow(
                eq(BusinessOnboardingWorkflow.WORKFLOW_ID), inputCaptor.capture(),
                anyString(), eq("api"), eq(false));
        assertThat(inputCaptor.getValue()).containsEntry(BusinessOnboardingWorkflow.INPUT_COMMAND, cmd);
    }

    @Test
    void getStatus_returnsCurrentPhase() {
        UUID onboardingId = TEST_ONBOARDING_ID;
        when(queryService.executeQuery(onboardingId.toString(), BusinessOnboardingWorkflow.QUERY_JOURNEY_STATUS))
                .thenReturn(Mono.just(MOCK_STATUS));

        StepVerifier.create(service.getStatus(onboardingId))
                .assertNext(status -> {
                    assertThat(status.getCurrentPhase()).isEqualTo(BusinessOnboardingWorkflow.PHASE_INITIATED);
                    assertThat(status.getNextStep()).isEqualTo(BusinessOnboardingWorkflow.STEP_RECEIVE_COMPANY_DATA);
                })
                .verifyComplete();
    }

    @Test
    void submitCompanyData_sendsSignal() {
        UUID onboardingId = TEST_ONBOARDING_ID;
        SubmitCompanyDataCommand cmd = SubmitCompanyDataCommand.builder()
                .legalName("Acme Corp SL")
                .taxId("B12345678")
                .build();

        when(signalService.signal(eq(onboardingId.toString()), eq(BusinessOnboardingWorkflow.SIGNAL_COMPANY_DATA), eq(cmd)))
                .thenReturn(Mono.just(SignalResult.delivered("test-id", "signal", "step")));

        StepVerifier.create(service.submitCompanyData(onboardingId, cmd))
                .verifyComplete();

        verify(signalService).signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_COMPANY_DATA, cmd);
    }

    @Test
    void submitUbos_sendsSignal() {
        UUID onboardingId = TEST_ONBOARDING_ID;
        SubmitUbosCommand cmd = SubmitUbosCommand.builder()
                .ubos(List.of(SubmitUbosCommand.UboEntry.builder()
                        .firstName("John").lastName("Doe").ownershipPercentage(new java.math.BigDecimal("51"))
                        .build()))
                .build();

        when(signalService.signal(eq(onboardingId.toString()), eq(BusinessOnboardingWorkflow.SIGNAL_UBOS), eq(cmd)))
                .thenReturn(Mono.just(SignalResult.delivered("test-id", "signal", "step")));

        StepVerifier.create(service.submitUbos(onboardingId, cmd))
                .verifyComplete();

        verify(signalService).signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_UBOS, cmd);
    }

    @Test
    void submitCorporateDocuments_sendsSignal() {
        UUID onboardingId = TEST_ONBOARDING_ID;
        SubmitCorporateDocumentsCommand cmd = SubmitCorporateDocumentsCommand.builder()
                .documents(List.of(SubmitCorporateDocumentsCommand.CorporateDocumentEntry.builder()
                        .documentType("ARTICLES_OF_INCORPORATION").documentReference("doc-ref-123")
                        .build()))
                .build();

        when(signalService.signal(eq(onboardingId.toString()), eq(BusinessOnboardingWorkflow.SIGNAL_CORPORATE_DOCS), eq(cmd)))
                .thenReturn(Mono.just(SignalResult.delivered("test-id", "signal", "step")));

        StepVerifier.create(service.submitCorporateDocuments(onboardingId, cmd))
                .verifyComplete();

        verify(signalService).signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_CORPORATE_DOCS, cmd);
    }

    @Test
    void submitAuthorizedSigners_sendsSignal() {
        UUID onboardingId = TEST_ONBOARDING_ID;
        SubmitAuthorizedSignersCommand cmd = SubmitAuthorizedSignersCommand.builder()
                .signers(List.of(SubmitAuthorizedSignersCommand.SignerEntry.builder()
                        .firstName("Jane").lastName("Smith").role("LEGAL_REPRESENTATIVE")
                        .build()))
                .build();

        when(signalService.signal(eq(onboardingId.toString()), eq(BusinessOnboardingWorkflow.SIGNAL_AUTHORIZED_SIGNERS), eq(cmd)))
                .thenReturn(Mono.just(SignalResult.delivered("test-id", "signal", "step")));

        StepVerifier.create(service.submitAuthorizedSigners(onboardingId, cmd))
                .verifyComplete();

        verify(signalService).signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_AUTHORIZED_SIGNERS, cmd);
    }

    @Test
    void triggerKybVerification_sendsSignal() {
        UUID onboardingId = TEST_ONBOARDING_ID;

        when(signalService.signal(eq(onboardingId.toString()), eq(BusinessOnboardingWorkflow.SIGNAL_KYB_TRIGGERED), any()))
                .thenReturn(Mono.just(SignalResult.delivered("test-id", "signal", "step")));

        StepVerifier.create(service.triggerKybVerification(onboardingId))
                .verifyComplete();

        verify(signalService).signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_KYB_TRIGGERED, null);
    }

    @Test
    void completeOnboarding_sendsSignal() {
        UUID onboardingId = TEST_ONBOARDING_ID;

        when(signalService.signal(eq(onboardingId.toString()), eq(BusinessOnboardingWorkflow.SIGNAL_COMPLETION), any()))
                .thenReturn(Mono.just(SignalResult.delivered("test-id", "signal", "step")));

        StepVerifier.create(service.completeOnboarding(onboardingId))
                .verifyComplete();

        verify(signalService).signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_COMPLETION, null);
    }

    @Test
    void getKybStatus_returnsKybInfo() {
        UUID onboardingId = TEST_ONBOARDING_ID;
        when(queryService.executeQuery(onboardingId.toString(), BusinessOnboardingWorkflow.QUERY_JOURNEY_STATUS))
                .thenReturn(Mono.just(MOCK_STATUS));

        StepVerifier.create(service.getKybStatus(onboardingId))
                .assertNext(kybStatus -> {
                    assertThat(kybStatus.getCaseId()).isEqualTo(MOCK_STATUS.getKybCaseId());
                    assertThat(kybStatus.getStatus()).isEqualTo("CASE_OPENED");
                })
                .verifyComplete();
    }
}
