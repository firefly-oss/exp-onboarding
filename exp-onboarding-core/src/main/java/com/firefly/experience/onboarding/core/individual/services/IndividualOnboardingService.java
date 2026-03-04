package com.firefly.experience.onboarding.core.individual.services;

import com.firefly.experience.onboarding.core.individual.commands.InitiateOnboardingCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import com.firefly.experience.onboarding.core.individual.queries.JourneyStatusDTO;
import com.firefly.experience.onboarding.core.individual.queries.KycStatusDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service interface for the individual onboarding journey.
 * Each method corresponds to an atomic endpoint that starts or advances the workflow.
 */
public interface IndividualOnboardingService {
    /** Start a new onboarding journey. Returns the partyId as journey identifier. */
    Mono<UUID> initiateOnboarding(InitiateOnboardingCommand command);

    /** Send personal data signal to advance the journey. */
    Mono<Void> submitPersonalData(UUID partyId, SubmitPersonalDataCommand command);

    /** Send identity documents signal to advance the journey. */
    Mono<Void> submitIdentityDocuments(UUID partyId, SubmitIdentityDocumentsCommand command);

    /** Send KYC trigger signal to advance the journey. */
    Mono<Void> triggerKyc(UUID partyId);

    /** Send completion signal to advance the journey. */
    Mono<Void> completeOnboarding(UUID partyId);

    /** Reconstruct current journey status from the workflow's execution state. */
    Mono<JourneyStatusDTO> getJourneyStatus(UUID partyId);

    /** Retrieve KYC verification status for the onboarding journey. */
    Mono<KycStatusDTO> getKycStatus(UUID partyId);
}
