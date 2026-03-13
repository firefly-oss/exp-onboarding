package com.firefly.experience.onboarding.core.business.services;

import com.firefly.experience.onboarding.core.business.commands.InitiateBusinessOnboardingCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCorporateDocumentsCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitUbosCommand;
import com.firefly.experience.onboarding.core.business.commands.UpdatePartialDataCommand;
import com.firefly.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
import com.firefly.experience.onboarding.core.business.queries.KybStatusDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service interface for the business onboarding journey.
 * Each method corresponds to an atomic endpoint that starts or advances the workflow.
 */
public interface BusinessOnboardingService {
    /** Start a new business onboarding journey. Returns the status with the onboarding ID. */
    Mono<BusinessOnboardingStatusDTO> initiateOnboarding(InitiateBusinessOnboardingCommand command);

    /** Reconstruct current journey status from the workflow's execution state. */
    Mono<BusinessOnboardingStatusDTO> getStatus(UUID onboardingId);

    /** Send company data signal to advance the journey. */
    Mono<Void> submitCompanyData(UUID onboardingId, SubmitCompanyDataCommand command);

    /** Send UBOs signal to advance the journey. */
    Mono<Void> submitUbos(UUID onboardingId, SubmitUbosCommand command);

    /** Send corporate documents signal to advance the journey. */
    Mono<Void> submitCorporateDocuments(UUID onboardingId, SubmitCorporateDocumentsCommand command);

    /** Send authorized signers signal to advance the journey. */
    Mono<Void> submitAuthorizedSigners(UUID onboardingId, SubmitAuthorizedSignersCommand command);

    /** Send KYB trigger signal to advance the journey. */
    Mono<Void> triggerKybVerification(UUID onboardingId);

    /** Send completion signal to advance the journey. */
    Mono<Void> completeOnboarding(UUID onboardingId);

    /** Retrieve KYB verification status for the onboarding journey. */
    Mono<KybStatusDTO> getKybStatus(UUID onboardingId);

    /**
     * Apply partial corrections to an in-progress onboarding without advancing the workflow.
     * Only non-null fields in the command are applied (contact info, business name).
     */
    Mono<Void> updatePartialData(UUID onboardingId, UpdatePartialDataCommand command);
}
