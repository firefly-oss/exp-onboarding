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


package io.fireflysoftware.experience.onboarding.core.individual.services;

import io.fireflysoftware.experience.onboarding.core.individual.commands.InitiateOnboardingCommand;
import io.fireflysoftware.experience.onboarding.core.individual.commands.SubmitEconomicDataCommand;
import io.fireflysoftware.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand;
import io.fireflysoftware.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import io.fireflysoftware.experience.onboarding.core.individual.queries.JourneyStatusDTO;
import io.fireflysoftware.experience.onboarding.core.individual.queries.KycStatusDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service interface for the individual onboarding journey.
 * Each method corresponds to an atomic endpoint that starts or advances the workflow.
 */
public interface IndividualOnboardingService {
    /** Start a new onboarding journey. Returns the onboarding status with the onboarding ID. */
    Mono<JourneyStatusDTO> initiateOnboarding(InitiateOnboardingCommand command);

    /** Send personal data signal to advance the journey. */
    Mono<Void> submitPersonalData(UUID onboardingId, SubmitPersonalDataCommand command);

    /** Send economic data signal to advance the journey past the economic-data gate. */
    Mono<Void> submitEconomicData(UUID onboardingId, SubmitEconomicDataCommand command);

    /** Send identity documents signal to advance the journey. */
    Mono<Void> submitIdentityDocuments(UUID onboardingId, SubmitIdentityDocumentsCommand command);

    /** Send KYC trigger signal to advance the journey. */
    Mono<Void> triggerKyc(UUID onboardingId);

    /** Send completion signal to advance the journey. */
    Mono<Void> completeOnboarding(UUID onboardingId);

    /** Reconstruct current journey status from the workflow's execution state. */
    Mono<JourneyStatusDTO> getJourneyStatus(UUID onboardingId);

    /** Retrieve KYC verification status for the onboarding journey. */
    Mono<KycStatusDTO> getKycStatus(UUID onboardingId);
}
