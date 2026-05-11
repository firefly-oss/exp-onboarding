package com.firefly.experience.onboarding.core.individual.services.impl;

import com.firefly.experience.onboarding.core.individual.commands.InitiateOnboardingCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitEconomicDataCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import com.firefly.experience.onboarding.core.individual.queries.JourneyStatusDTO;
import com.firefly.experience.onboarding.core.individual.queries.KycStatusDTO;
import com.firefly.experience.onboarding.core.individual.services.IndividualOnboardingService;
import com.firefly.experience.onboarding.core.individual.workflows.IndividualOnboardingWorkflow;
import org.fireflyframework.orchestration.core.model.ExecutionStatus;
import org.fireflyframework.orchestration.workflow.engine.WorkflowEngine;
import org.fireflyframework.orchestration.workflow.query.WorkflowQueryService;
import org.fireflyframework.orchestration.workflow.signal.SignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
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

        // ASYNC mode: startWorkflow returns immediately with state RUNNING while the
        // engine executes steps in the background. The workflow contains @WaitForSignal
        // gates, so SYNC mode would block the HTTP request indefinitely waiting for
        // signals that the caller can only send via subsequent HTTP calls.
        //
        // After triggering the workflow we poll the persisted execution state until either
        //   (a) the first synchronous step `register-party` has produced a partyId — at
        //       which point the caller has everything it needs to drive the journey, or
        //   (b) the workflow reaches a terminal FAILED/CANCELLED/TIMED_OUT state — which
        //       we surface as an HTTP error instead of returning a half-empty payload.
        return workflowEngine.startWorkflow(IndividualOnboardingWorkflow.WORKFLOW_ID, input, correlationId, "api", false)
                .then(awaitInitialPhase(correlationId))
                .doOnNext(status ->
                        log.info("Initiated onboarding journey: onboardingId={}", correlationId));
    }

    /**
     * Poll the workflow until {@code register-party} produces a partyId (success path) or
     * the workflow reaches a terminal failure state. Returns the {@link JourneyStatusDTO}
     * snapshot at that moment.
     */
    private Mono<JourneyStatusDTO> awaitInitialPhase(String correlationId) {
        Duration interval = Duration.ofMillis(100);
        Duration maxWait  = Duration.ofSeconds(30);

        return Mono.defer(() -> queryService.executeQuery(correlationId, IndividualOnboardingWorkflow.QUERY_JOURNEY_STATUS)
                        .cast(JourneyStatusDTO.class))
                .flatMap(this::failIfTerminal)
                .repeatWhenEmpty(flux -> flux.delayElements(interval))
                .timeout(maxWait, Mono.error(new BusinessException(HttpStatus.GATEWAY_TIMEOUT,
                        "ONBOARDING_INITIATION_TIMEOUT",
                        "Onboarding journey did not reach the first gate within " + maxWait.toSeconds() + "s")));
    }

    /**
     * If the snapshot represents a terminal failure, surface it as a 502. If partyId is
     * still null, treat the snapshot as not-yet-ready (Mono.empty) so the poll loop retries.
     * Otherwise, emit the snapshot.
     */
    private Mono<JourneyStatusDTO> failIfTerminal(JourneyStatusDTO snapshot) {
        if (snapshot == null) {
            return Mono.empty();
        }
        Object phase = snapshot.getCurrentPhase();
        if (phase != null) {
            String name = phase.toString();
            if ("FAILED".equals(name) || "CANCELLED".equals(name) || "TIMED_OUT".equals(name)) {
                return Mono.error(new BusinessException(HttpStatus.BAD_GATEWAY,
                        "ONBOARDING_INITIATION_FAILED",
                        "Onboarding journey could not be initiated: " + name));
            }
        }
        if (snapshot.getPartyId() == null) {
            return Mono.empty();
        }
        return Mono.just(snapshot);
    }

    @Override
    public Mono<Void> submitPersonalData(UUID onboardingId, SubmitPersonalDataCommand command) {
        return signalService.signal(onboardingId.toString(), IndividualOnboardingWorkflow.SIGNAL_PERSONAL_DATA, command)
                .doOnNext(r -> log.info("Signal delivered: personal-data-submitted for onboardingId={}",
                        onboardingId))
                .then();
    }

    @Override
    public Mono<Void> submitEconomicData(UUID onboardingId, SubmitEconomicDataCommand command) {
        return signalService.signal(onboardingId.toString(), IndividualOnboardingWorkflow.SIGNAL_ECONOMIC_DATA, command)
                .doOnNext(r -> log.info("Signal delivered: economic-data-submitted for onboardingId={}",
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
