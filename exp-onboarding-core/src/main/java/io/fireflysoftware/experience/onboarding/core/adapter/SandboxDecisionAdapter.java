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


package io.fireflysoftware.experience.onboarding.core.adapter;

import io.fireflysoftware.experience.onboarding.core.ports.DecisionPort;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionRequest;
import io.fireflysoftware.experience.onboarding.core.ports.DecisionResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Local sample used only when the decision service process is not available.
 * Every request is {@code APPROVE} with reason code {@code SANDBOX_APPROVE}.
 * This is not the rules engine. The running journey uses
 * {@code POST /api/v1/decisions} on domain-lending-credit-scoring unless
 * {@code firefly.experience.decision.adapter=sandbox}.
 */
@Component
@ConditionalOnProperty(name = "firefly.experience.decision.adapter", havingValue = "sandbox")
public class SandboxDecisionAdapter implements DecisionPort {

    @Override
    public Mono<DecisionResult> decide(DecisionRequest request) {
        return Mono.just(DecisionResult.approve());
    }
}
