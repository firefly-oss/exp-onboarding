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


package io.fireflysoftware.experience.onboarding.web.dto;

import io.fireflysoftware.experience.onboarding.core.business.queries.KybStatusDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Web-layer response DTO for KYB verification status.
 * Wraps {@link KybStatusDTO} from the service layer so that the HTTP API contract
 * is decoupled from the core module's internal types.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KybStatusResponse {

    /** Identifier of the KYB case in the domain KYB/KYC service. */
    private UUID caseId;

    /**
     * Current KYB verification status.
     * Possible values: {@code NOT_STARTED}, {@code CASE_OPENED}, {@code IN_PROGRESS},
     * {@code VERIFIED}.
     */
    private String status;

    /**
     * Maps a service-layer {@link KybStatusDTO} into this web-layer response.
     *
     * @param dto the service-layer DTO to convert
     * @return a {@code KybStatusResponse} with all fields populated
     */
    public static KybStatusResponse from(KybStatusDTO dto) {
        return KybStatusResponse.builder()
                .caseId(dto.getCaseId())
                .status(dto.getStatus())
                .build();
    }
}
