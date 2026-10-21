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

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Resolves the actor before an access check. The default adapter calls
 * {@code POST /api/v1/identity/subjects} on domain-core-security-center.
 * Experience code does not call Keycloak, Cognito, or an internal user store.
 */
public interface IdentityPort {

    Mono<SubjectRef> resolve(String username);

    /**
     * Actor returned by the security-center sandbox for username {@code sample-journey}.
     */
    UUID SAMPLE_ACTOR = UUID.fromString("11111111-1111-4111-8111-111111111111");

    /**
     * Actor returned for username {@code denied-actor}. Access checks return {@code ACTOR_NOT_ENTITLED}.
     */
    UUID DENIED_ACTOR = UUID.fromString("22222222-2222-4222-8222-222222222222");

    /**
     * Actor reserved for username {@code sample-employee}.
     */
    UUID EMPLOYEE_ACTOR = UUID.fromString("33333333-3333-4333-8333-333333333333");

    String SAMPLE_USERNAME = "sample-journey";
}
