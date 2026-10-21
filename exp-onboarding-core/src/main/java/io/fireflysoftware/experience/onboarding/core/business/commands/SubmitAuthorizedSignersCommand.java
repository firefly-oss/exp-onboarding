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


package io.fireflysoftware.experience.onboarding.core.business.commands;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Command DTO for submitting authorized signers during business onboarding.
 * Advances the journey past the authorized-signers-submitted signal gate.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitAuthorizedSignersCommand {
    @Valid
    private List<SignerEntry> signers;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SignerEntry {
        private String firstName;
        private String lastName;
        private String documentType;
        private String documentNumber;
        private String role;
        private String powerDocumentReference;

        /**
         * Signer email address.
         */
        @Email(message = "email must be a valid email address")
        private String email;

        /**
         * Whether this signer is authorized to sign on behalf of the legal entity.
         */
        private Boolean signingAuthorized;

        /**
         * Whether this signer is a Politically Exposed Person.
         */
        private Boolean isPep;
    }
}
