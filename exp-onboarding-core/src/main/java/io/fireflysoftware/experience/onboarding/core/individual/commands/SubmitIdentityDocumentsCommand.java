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


package io.fireflysoftware.experience.onboarding.core.individual.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Command DTO for submitting identity documents during the onboarding journey.
 * Sent as signal payload to advance the workflow past the identity-documents gate.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitIdentityDocumentsCommand {
    @NotBlank
    private String documentType;
    @NotBlank
    private String documentNumber;
    private String documentContent;
    private String mimeType;
}
