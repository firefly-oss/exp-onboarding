package com.firefly.experience.onboarding.core.individual.services.impl;

import com.firefly.experience.onboarding.core.individual.commands.InitiateOnboardingCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import com.firefly.experience.onboarding.core.individual.queries.JourneyStatusDTO;
import com.firefly.experience.onboarding.core.individual.workflows.IndividualOnboardingWorkflow;
import org.fireflyframework.orchestration.core.model.ExecutionPattern;
import org.fireflyframework.orchestration.core.model.ExecutionStatus;
import org.fireflyframework.orchestration.core.persistence.ExecutionState;
import org.fireflyframework.orchestration.workflow.engine.WorkflowEngine;
import org.fireflyframework.orchestration.workflow.query.WorkflowQueryService;
import org.fireflyframework.orchestration.workflow.signal.SignalResult;
import org.fireflyframework.orchestration.workflow.signal.SignalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.firefly.experience.onboarding.core.individual.workflows.IndividualOnboardingWorkflow.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class IndividualOnboardingServiceImplTest {

    @Mock private WorkflowEngine workflowEngine;
    @Mock private SignalService signalService;
    @Mock private WorkflowQueryService queryService;

    private IndividualOnboardingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IndividualOnboardingServiceImpl(workflowEngine, signalService, queryService);
    }

    @Test
    void initiateOnboarding_startsWorkflowAndReturnsJourneyStatus() {
        UUID partyId = UUID.randomUUID();

        ExecutionState state = new ExecutionState(
                "corr-id", WORKFLOW_ID, ExecutionPattern.WORKFLOW,
                ExecutionStatus.SUSPENDED,
                Map.of(STEP_REGISTER_PARTY, partyId),   // stepResults
                Map.of(), Map.of(), Map.of(),             // stepStatuses, attempts, latencies
                Map.of(), Map.of(),                       // variables, headers
                Set.of(), List.of(),                      // idempotencyKeys, topologyLayers
                null, Instant.now(), Instant.now(),       // failureReason, startedAt, updatedAt
                Optional.empty()                          // report
        );

        JourneyStatusDTO statusDTO = JourneyStatusDTO.builder()
                .partyId(partyId)
                .currentPhase(PHASE_AWAITING_PERSONAL_DATA)
                .completedSteps(List.of(STEP_REGISTER_PARTY, STEP_OPEN_KYC_CASE, STEP_SEND_WELCOME))
                .nextStep(STEP_RECEIVE_PERSONAL_DATA)
                .build();

        when(workflowEngine.startWorkflow(eq(WORKFLOW_ID), any(Map.class), any(String.class), eq("api"), eq(false)))
                .thenReturn(Mono.just(state));
        when(queryService.executeQuery(any(String.class), eq(QUERY_JOURNEY_STATUS)))
                .thenReturn(Mono.just(statusDTO));

        InitiateOnboardingCommand cmd = InitiateOnboardingCommand.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .build();

        StepVerifier.create(service.initiateOnboarding(cmd))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getPartyId()).isEqualTo(partyId);
                    assertThat(result.getCurrentPhase()).isEqualTo(PHASE_AWAITING_PERSONAL_DATA);
                })
                .verifyComplete();

        // Asserts QA Issue 2 fix: the workflow input map keys the command under
        // INPUT_COMMAND so the @Input(INPUT_COMMAND) resolver can extract it.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(workflowEngine).startWorkflow(eq(WORKFLOW_ID), inputCaptor.capture(), any(String.class), eq("api"), eq(false));
        assertThat(inputCaptor.getValue()).containsEntry(INPUT_COMMAND, cmd);
    }

    @Test
    void submitPersonalData_sendsSignal() {
        UUID onboardingId = UUID.randomUUID();
        SignalResult signalResult = SignalResult.delivered(onboardingId.toString(), SIGNAL_PERSONAL_DATA, STEP_RECEIVE_PERSONAL_DATA);

        when(signalService.signal(eq(onboardingId.toString()), eq(SIGNAL_PERSONAL_DATA), any()))
                .thenReturn(Mono.just(signalResult));

        SubmitPersonalDataCommand cmd = SubmitPersonalDataCommand.builder()
                .dateOfBirth("1990-01-15")
                .nationality("ES")
                .addressLine1("Calle Mayor 1")
                .city("Madrid")
                .postalCode("28001")
                .country("ES")
                .build();

        StepVerifier.create(service.submitPersonalData(onboardingId, cmd))
                .verifyComplete();

        verify(signalService).signal(eq(onboardingId.toString()), eq(SIGNAL_PERSONAL_DATA), eq(cmd));
    }

    @Test
    void submitIdentityDocuments_sendsSignal() {
        UUID onboardingId = UUID.randomUUID();
        SignalResult signalResult = SignalResult.delivered(onboardingId.toString(), SIGNAL_IDENTITY_DOCS, STEP_RECEIVE_IDENTITY_DOCS);

        when(signalService.signal(eq(onboardingId.toString()), eq(SIGNAL_IDENTITY_DOCS), any()))
                .thenReturn(Mono.just(signalResult));

        SubmitIdentityDocumentsCommand cmd = SubmitIdentityDocumentsCommand.builder()
                .documentType("PASSPORT")
                .documentNumber("AB123456")
                .build();

        StepVerifier.create(service.submitIdentityDocuments(onboardingId, cmd))
                .verifyComplete();

        verify(signalService).signal(eq(onboardingId.toString()), eq(SIGNAL_IDENTITY_DOCS), eq(cmd));
    }

    @Test
    void triggerKyc_sendsSignal() {
        UUID onboardingId = UUID.randomUUID();
        SignalResult signalResult = SignalResult.delivered(onboardingId.toString(), SIGNAL_KYC_TRIGGERED, STEP_TRIGGER_KYC_VERIFICATION);

        when(signalService.signal(eq(onboardingId.toString()), eq(SIGNAL_KYC_TRIGGERED), eq(null)))
                .thenReturn(Mono.just(signalResult));

        StepVerifier.create(service.triggerKyc(onboardingId))
                .verifyComplete();

        verify(signalService).signal(eq(onboardingId.toString()), eq(SIGNAL_KYC_TRIGGERED), eq(null));
    }

    @Test
    void completeOnboarding_sendsSignal() {
        UUID onboardingId = UUID.randomUUID();
        SignalResult signalResult = SignalResult.delivered(onboardingId.toString(), SIGNAL_COMPLETION, STEP_VERIFY_KYC_APPROVED);

        when(signalService.signal(eq(onboardingId.toString()), eq(SIGNAL_COMPLETION), eq(null)))
                .thenReturn(Mono.just(signalResult));

        StepVerifier.create(service.completeOnboarding(onboardingId))
                .verifyComplete();

        verify(signalService).signal(eq(onboardingId.toString()), eq(SIGNAL_COMPLETION), eq(null));
    }

    @Test
    void getJourneyStatus_queriesWorkflow() {
        UUID onboardingId = UUID.randomUUID();
        UUID kycCaseId = UUID.randomUUID();

        JourneyStatusDTO dto = JourneyStatusDTO.builder()
                .partyId(onboardingId)
                .kycCaseId(kycCaseId)
                .currentPhase(PHASE_AWAITING_PERSONAL_DATA)
                .completedSteps(List.of(STEP_REGISTER_PARTY, STEP_OPEN_KYC_CASE, STEP_SEND_WELCOME))
                .nextStep(STEP_RECEIVE_PERSONAL_DATA)
                .build();

        when(queryService.executeQuery(eq(onboardingId.toString()), eq(QUERY_JOURNEY_STATUS)))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(service.getJourneyStatus(onboardingId))
                .assertNext(status -> {
                    assertThat(status.getCurrentPhase()).isEqualTo(PHASE_AWAITING_PERSONAL_DATA);
                    assertThat(status.getCompletedSteps()).contains(STEP_REGISTER_PARTY);
                    assertThat(status.getNextStep()).isEqualTo(STEP_RECEIVE_PERSONAL_DATA);
                })
                .verifyComplete();
    }

    @Test
    void getKycStatus_queriesWorkflowAndMapsToKycDto() {
        UUID onboardingId = UUID.randomUUID();
        UUID kycCaseId = UUID.randomUUID();

        JourneyStatusDTO journeyDto = JourneyStatusDTO.builder()
                .partyId(onboardingId)
                .kycCaseId(kycCaseId)
                .currentPhase(PHASE_AWAITING_KYC_TRIGGER)
                .completedSteps(List.of(STEP_REGISTER_PARTY, STEP_OPEN_KYC_CASE))
                .kycVerificationStatus(KYC_STATUS_PENDING)
                .build();

        when(queryService.executeQuery(eq(onboardingId.toString()), eq(QUERY_JOURNEY_STATUS)))
                .thenReturn(Mono.just(journeyDto));

        StepVerifier.create(service.getKycStatus(onboardingId))
                .assertNext(kycStatus -> {
                    assertThat(kycStatus.getCaseId()).isEqualTo(kycCaseId);
                    assertThat(kycStatus.getStatus()).isEqualTo(KYC_STATUS_PENDING);
                })
                .verifyComplete();
    }
}
