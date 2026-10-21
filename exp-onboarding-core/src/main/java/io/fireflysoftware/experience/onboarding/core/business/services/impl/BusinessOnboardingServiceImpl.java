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


package io.fireflysoftware.experience.onboarding.core.business.services.impl;

import io.fireflysoftware.domain.people.sdk.api.BusinessesApi;
import io.fireflysoftware.domain.people.sdk.model.UpdateBusinessCommand;
import io.fireflysoftware.experience.onboarding.core.business.commands.InitiateBusinessOnboardingCommand;
import io.fireflysoftware.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import io.fireflysoftware.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import io.fireflysoftware.experience.onboarding.core.business.commands.SubmitCorporateDocumentsCommand;
import io.fireflysoftware.experience.onboarding.core.business.commands.SubmitUbosCommand;
import io.fireflysoftware.experience.onboarding.core.business.commands.UpdatePartialDataCommand;
import io.fireflysoftware.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
import io.fireflysoftware.experience.onboarding.core.business.queries.KybStatusDTO;
import io.fireflysoftware.experience.onboarding.core.business.services.BusinessOnboardingService;
import io.fireflysoftware.experience.onboarding.core.business.workflows.BusinessOnboardingWorkflow;
import io.fireflysoftware.experience.onboarding.core.ports.AccessAction;
import io.fireflysoftware.experience.onboarding.core.ports.AccessGuard;
import io.fireflysoftware.experience.onboarding.core.ports.AccessPort;
import io.fireflysoftware.experience.onboarding.core.ports.IdentityPort;
import io.fireflysoftware.experience.onboarding.core.ports.AccessRequest;
import io.fireflysoftware.experience.onboarding.core.ports.CreditReportPort;
import io.fireflysoftware.experience.onboarding.core.ports.CreditReportQuery;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionPort;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionRequest;
import io.fireflysoftware.experience.onboarding.core.ports.JourneyStep;
import io.fireflysoftware.experience.onboarding.core.ports.RiskStep;
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
    private final AccessPort accessPort;
    private final IdentityPort identityPort;
    private final CreditReportPort creditReportPort;
    private final DecisionPort decisionPort;

    @Override
    public Mono<BusinessOnboardingStatusDTO> initiateOnboarding(InitiateBusinessOnboardingCommand command) {
        UUID partyId = command.getPartyId() != null ? command.getPartyId() : command.getTenantId();
        UUID actorId = command.getActorId() != null ? command.getActorId() : partyId;
        return AccessGuard.require(identityPort, accessPort, IdentityPort.SAMPLE_USERNAME,
                AccessRequest.of(actorId, AccessAction.CREATE, "PARTY", partyId),
                () -> RiskStep.afterAccess(
                        creditReportPort,
                        decisionPort,
                        new CreditReportQuery(partyId, null, JourneyStep.ONBOARDING, Map.of()),
                        new DecisionRequest(partyId, null, JourneyStep.ONBOARDING, Map.of()),
                        () -> startWorkflow(command)));
    }

    private Mono<BusinessOnboardingStatusDTO> startWorkflow(InitiateBusinessOnboardingCommand command) {
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
