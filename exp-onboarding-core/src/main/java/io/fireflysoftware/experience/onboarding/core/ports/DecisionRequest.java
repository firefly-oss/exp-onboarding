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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Risk decision requested by a journey. The experience service does not score the attributes.
 *
 * @param partyId     party the decision is about. May be null before the party exists.
 * @param productId   optional product
 * @param journeyStep journey step being decided
 * @param attributes  facts already collected, including a credit report summary when one was requested
 */
public record DecisionRequest(UUID partyId, UUID productId, JourneyStep journeyStep, Map<String, String> attributes) {

    public DecisionRequest {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public DecisionRequest withAttribute(String key, String value) {
        Map<String, String> copy = new HashMap<>(attributes);
        if (key != null && value != null) {
            copy.put(key, value);
        }
        return new DecisionRequest(partyId, productId, journeyStep, copy);
    }
}
