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

/**
 * Result of an {@link AccessPort} check. A denial must not be followed by a domain call.
 */
public record AccessDecision(boolean allowed, String reason) {

    public static AccessDecision grant() {
        return permit("SANDBOX_ALLOW");
    }

    public static AccessDecision permit(String reason) {
        return new AccessDecision(true, reason);
    }

    public static AccessDecision denied(String reason) {
        return new AccessDecision(false, reason == null ? "ACCESS_DENIED" : reason);
    }
}
