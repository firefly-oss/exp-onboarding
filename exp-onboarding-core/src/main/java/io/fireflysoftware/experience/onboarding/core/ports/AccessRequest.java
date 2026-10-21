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

import java.util.UUID;

/**
 * Security check requested by a journey before a domain call.
 *
 * @param actorId     caller. When omitted, the resource id is used so sample journeys can run.
 * @param action      read, create, update, close, or decide
 * @param resourceType customer, account, policy, claim, application, or card
 * @param resourceId   resource being read or changed. Null when the resource does not exist yet.
 */
public record AccessRequest(UUID actorId, AccessAction action, String resourceType, UUID resourceId) {

    public AccessRequest {
        if (actorId == null) {
            actorId = resourceId;
        }
    }

    public static AccessRequest of(UUID actorId, AccessAction action, String resourceType, UUID resourceId) {
        return new AccessRequest(actorId, action, resourceType, resourceId);
    }
}
