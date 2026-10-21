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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Journey entry order: identity, then access, then the supplied domain call.
 * A denial does not invoke the domain supplier.
 */
public final class AccessGuard {

    private AccessGuard() {
    }

    public static <T> Mono<T> require(IdentityPort identity,
                                      AccessPort access,
                                      String username,
                                      AccessRequest request,
                                      Supplier<Mono<T>> domainCall) {
        return check(identity, access, username, request).flatMap(decision -> decision.allowed()
                ? domainCall.get()
                : Mono.error(new AccessDeniedException(decision.reason())));
    }

    public static <T> Flux<T> requireMany(IdentityPort identity,
                                          AccessPort access,
                                          String username,
                                          AccessRequest request,
                                          Supplier<Flux<T>> domainCall) {
        return check(identity, access, username, request).flatMapMany(decision -> decision.allowed()
                ? domainCall.get()
                : Flux.error(new AccessDeniedException(decision.reason())));
    }

    private static Mono<AccessDecision> check(IdentityPort identity,
                                              AccessPort access,
                                              String username,
                                              AccessRequest request) {
        String user = username == null || username.isBlank() ? IdentityPort.SAMPLE_USERNAME : username;
        return identity.resolve(user)
                .switchIfEmpty(Mono.error(new AccessDeniedException("ACTOR_UNKNOWN")))
                .flatMap(subject -> access.check(withActor(subject.actorId(), request)));
    }

    private static AccessRequest withActor(UUID actorId, AccessRequest request) {
        UUID resourceId = request.resourceId() != null ? request.resourceId() : sampleResource(request.resourceType());
        return new AccessRequest(actorId, request.action(), request.resourceType(), resourceId);
    }

    /**
     * Sample resource ids the security-center sandbox grants to {@code sample-journey}
     * for READ and CREATE.
     */
    public static UUID sampleResource(String resourceType) {
        return switch (resourceType == null ? "" : resourceType) {
            case "ACCOUNT" -> UUID.fromString("10000000-0000-4000-8000-000000000002");
            case "CARD" -> UUID.fromString("10000000-0000-4000-8000-000000000003");
            case "APPLICATION" -> UUID.fromString("10000000-0000-4000-8000-000000000004");
            case "POLICY" -> UUID.fromString("10000000-0000-4000-8000-000000000005");
            case "CLAIM" -> UUID.fromString("10000000-0000-4000-8000-000000000006");
            default -> UUID.fromString("10000000-0000-4000-8000-000000000001");
        };
    }
}
