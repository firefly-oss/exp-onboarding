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


package io.fireflysoftware.experience.onboarding.core.ports;

import java.util.List;

/**
 * Risk decision. {@link DecisionOutcome#DECLINE} stops the journey before the domain command.
 */
public record DecisionResult(DecisionOutcome outcome, List<String> reasonCodes) {

    public DecisionResult {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public static DecisionResult approve() {
        return new DecisionResult(DecisionOutcome.APPROVE, List.of("SANDBOX_APPROVE"));
    }
}
