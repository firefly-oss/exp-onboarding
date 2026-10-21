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


package io.fireflysoftware.experience.onboarding.core.business.queries;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO representing the current state of a business onboarding journey.
 * Reconstructed from the workflow's internal execution state via @WorkflowQuery.
 * The frontend uses {@code currentPhase} and {@code nextStep} to determine
 * which screen to show and resume from after interruption.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BusinessOnboardingStatusDTO {
    private UUID onboardingId;
    private UUID partyId;
    private UUID kybCaseId;
    private String currentPhase;
    private List<String> completedSteps;
    private String nextStep;
    private String kybStatus;
}
