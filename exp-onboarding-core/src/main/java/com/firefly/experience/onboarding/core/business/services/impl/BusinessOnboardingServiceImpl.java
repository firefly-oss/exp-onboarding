package com.firefly.experience.onboarding.core.business.services.impl;

import com.firefly.domain.people.sdk.api.BusinessesApi;
import com.firefly.domain.people.sdk.model.UpdateBusinessCommand;
import com.firefly.experience.onboarding.core.business.commands.InitiateBusinessOnboardingCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCorporateDocumentsCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitUbosCommand;
import com.firefly.experience.onboarding.core.business.commands.UpdatePartialDataCommand;
import com.firefly.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
import com.firefly.experience.onboarding.core.business.queries.KybStatusDTO;
import com.firefly.experience.onboarding.core.business.services.BusinessOnboardingService;
import com.firefly.experience.onboarding.core.business.workflows.BusinessOnboardingWorkflow;
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
 * Signal-driven workflow implementation of the business onboarding service.
 * <p>
 * The initiation endpoint starts a long-running workflow (SYNC mode — blocks until the
 * first @WaitForSignal gate). All subsequent endpoints send signals to advance the workflow
 * past its gates. The status endpoint queries the persisted execution state.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class BusinessOnboardingServiceImpl implements BusinessOnboardingService {

    private final WorkflowEngine workflowEngine;
    private final SignalService signalService;
    private final WorkflowQueryService queryService;
    private final BusinessesApi businessesApi;

    @Override
    public Mono<BusinessOnboardingStatusDTO> initiateOnboarding(InitiateBusinessOnboardingCommand command) {
        String correlationId = UUID.randomUUID().toString();
        Map<String, Object> input = Map.of(BusinessOnboardingWorkflow.INPUT_COMMAND, command);

        return workflowEngine.startWorkflow(BusinessOnboardingWorkflow.WORKFLOW_ID, input, correlationId, "api", false)
                .flatMap(state -> queryService.executeQuery(correlationId, BusinessOnboardingWorkflow.QUERY_JOURNEY_STATUS))
                .cast(BusinessOnboardingStatusDTO.class)
                .doOnNext(status ->
                        log.info("Initiated business onboarding: onboardingId={}", correlationId));
    }

    @Override
    public Mono<BusinessOnboardingStatusDTO> getStatus(UUID onboardingId) {
        return queryService.executeQuery(onboardingId.toString(), BusinessOnboardingWorkflow.QUERY_JOURNEY_STATUS)
                .cast(BusinessOnboardingStatusDTO.class);
    }

    @Override
    public Mono<Void> submitCompanyData(UUID onboardingId, SubmitCompanyDataCommand command) {
        return signalService.signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_COMPANY_DATA, command)
                .doOnNext(r -> log.info("Signal delivered: company-data-submitted for onboardingId={}", onboardingId))
                .then();
    }

    @Override
    public Mono<Void> submitUbos(UUID onboardingId, SubmitUbosCommand command) {
        return signalService.signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_UBOS, command)
                .doOnNext(r -> log.info("Signal delivered: ubos-submitted for onboardingId={}", onboardingId))
                .then();
    }

    @Override
    public Mono<Void> submitCorporateDocuments(UUID onboardingId, SubmitCorporateDocumentsCommand command) {
        return signalService.signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_CORPORATE_DOCS, command)
                .doOnNext(r -> log.info("Signal delivered: corporate-documents-submitted for onboardingId={}", onboardingId))
                .then();
    }

    @Override
    public Mono<Void> submitAuthorizedSigners(UUID onboardingId, SubmitAuthorizedSignersCommand command) {
        return signalService.signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_AUTHORIZED_SIGNERS, command)
                .doOnNext(r -> log.info("Signal delivered: authorized-signers-submitted for onboardingId={}", onboardingId))
                .then();
    }

    @Override
    public Mono<Void> triggerKybVerification(UUID onboardingId) {
        return signalService.signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_KYB_TRIGGERED, null)
                .doOnNext(r -> log.info("Signal delivered: kyb-triggered for onboardingId={}", onboardingId))
                .then();
    }

    @Override
    public Mono<Void> completeOnboarding(UUID onboardingId) {
        return signalService.signal(onboardingId.toString(), BusinessOnboardingWorkflow.SIGNAL_COMPLETION, null)
                .doOnNext(r -> log.info("Signal delivered: completion-requested for onboardingId={}", onboardingId))
                .then();
    }

    @Override
    public Mono<KybStatusDTO> getKybStatus(UUID onboardingId) {
        return queryService.executeQuery(onboardingId.toString(), BusinessOnboardingWorkflow.QUERY_JOURNEY_STATUS)
                .cast(BusinessOnboardingStatusDTO.class)
                .map(journey -> KybStatusDTO.builder()
                        .caseId(journey.getKybCaseId())
                        .status(journey.getKybStatus())
                        .build());
    }

    @Override
    public Mono<Void> updatePartialData(UUID onboardingId, UpdatePartialDataCommand command) {
        return queryService.executeQuery(onboardingId.toString(), BusinessOnboardingWorkflow.QUERY_JOURNEY_STATUS)
                .cast(BusinessOnboardingStatusDTO.class)
                .flatMap(status -> {
                    UpdateBusinessCommand updateCmd = new UpdateBusinessCommand()
                            .partyId(status.getPartyId());
                    if (command.getBusinessName() != null) {
                        updateCmd.legalName(command.getBusinessName());
                    }
                    return businessesApi.updateBusiness(updateCmd, UUID.randomUUID().toString()).then();
                })
                .doOnSuccess(v -> log.info("Partial data updated for onboardingId={}", onboardingId));
    }
}
