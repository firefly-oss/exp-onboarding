package com.firefly.experience.onboarding.core.individual.services.impl;

import com.firefly.experience.onboarding.core.individual.commands.InitiateOnboardingCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import com.firefly.experience.onboarding.core.individual.queries.JourneyStatusDTO;
import com.firefly.experience.onboarding.core.individual.queries.KycStatusDTO;
import com.firefly.experience.onboarding.core.individual.services.IndividualOnboardingService;
import com.firefly.experience.onboarding.core.individual.workflows.IndividualOnboardingWorkflow;
import org.fireflyframework.orchestration.workflow.engine.WorkflowEngine;
import org.fireflyframework.orchestration.workflow.query.WorkflowQueryService;
import org.fireflyframework.orchestration.workflow.signal.SignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Signal-driven workflow implementation of the individual onboarding service.
 * <p>
 * The initiation endpoint starts a long-running workflow (SYNC mode — blocks until the
 * first @WaitForSignal gate). All subsequent endpoints send signals to advance the workflow
 * past its gates. The status endpoint queries the persisted execution state.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class IndividualOnboardingServiceImpl implements IndividualOnboardingService {

    private final WorkflowEngine workflowEngine;
    private final SignalService signalService;
    private final WorkflowQueryService queryService;

    @Override
    public Mono<JourneyStatusDTO> initiateOnboarding(InitiateOnboardingCommand command) {
        String correlationId = UUID.randomUUID().toString();
        Map<String, Object> input = Map.of(IndividualOnboardingWorkflow.INPUT_COMMAND, command);

        // SYNC mode: executes steps until the first @WaitForSignal gate, then returns.
        // At that point, register-party step has completed and partyId is available.
        return workflowEngine.startWorkflow(IndividualOnboardingWorkflow.WORKFLOW_ID, input, correlationId, "api", false)
                .flatMap(state -> queryService.executeQuery(correlationId, IndividualOnboardingWorkflow.QUERY_JOURNEY_STATUS))
                .cast(JourneyStatusDTO.class)
                .doOnNext(status ->
                        log.info("Initiated onboarding journey: onboardingId={}", correlationId));
    }

    @Override
    public Mono<Void> submitPersonalData(UUID onboardingId, SubmitPersonalDataCommand command) {
        return signalService.signal(onboardingId.toString(), IndividualOnboardingWorkflow.SIGNAL_PERSONAL_DATA, command)
                .doOnNext(r -> log.info("Signal delivered: personal-data-submitted for onboardingId={}",
                        onboardingId))
                .then();
    }

    @Override
    public Mono<Void> submitIdentityDocuments(UUID onboardingId, SubmitIdentityDocumentsCommand command) {
        return signalService.signal(onboardingId.toString(), IndividualOnboardingWorkflow.SIGNAL_IDENTITY_DOCS, command)
                .doOnNext(r -> log.info("Signal delivered: identity-docs-submitted for onboardingId={}",
                        onboardingId))
                .then();
    }

    @Override
    public Mono<Void> triggerKyc(UUID onboardingId) {
        return signalService.signal(onboardingId.toString(), IndividualOnboardingWorkflow.SIGNAL_KYC_TRIGGERED, null)
                .doOnNext(r -> log.info("Signal delivered: kyc-triggered for onboardingId={}",
                        onboardingId))
                .then();
    }

    @Override
    public Mono<Void> completeOnboarding(UUID onboardingId) {
        return signalService.signal(onboardingId.toString(), IndividualOnboardingWorkflow.SIGNAL_COMPLETION, null)
                .doOnNext(r -> log.info("Signal delivered: completion-requested for onboardingId={}",
                        onboardingId))
                .then();
    }

    @Override
    public Mono<JourneyStatusDTO> getJourneyStatus(UUID onboardingId) {
        return queryService.executeQuery(onboardingId.toString(), IndividualOnboardingWorkflow.QUERY_JOURNEY_STATUS)
                .cast(JourneyStatusDTO.class);
    }

    @Override
    public Mono<KycStatusDTO> getKycStatus(UUID onboardingId) {
        return queryService.executeQuery(onboardingId.toString(), IndividualOnboardingWorkflow.QUERY_JOURNEY_STATUS)
                .cast(JourneyStatusDTO.class)
                .map(journey -> KycStatusDTO.builder()
                        .caseId(journey.getKycCaseId())
                        .status(journey.getKycVerificationStatus())
                        .build());
    }
}
