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

import io.fireflysoftware.experience.onboarding.core.ports.CreditReportPort;
import io.fireflysoftware.experience.onboarding.core.ports.CreditReportQuery;
import io.fireflysoftware.experience.onboarding.core.ports.CreditReportView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Local illustrative credit report. Score {@value #EXAMPLE_CREDIT_SCORE} matches
 * {@code SandboxCreditReportAdapter} in domain-lending-credit-scoring and is below the
 * sample ruleset. The lending minimum score is a business parameter, not a constant here.
 * This adapter does not call a bureau.
 * <p>
 * The decision service applies its own sandbox report when the journey does not send a score.
 * Replace the bureau behind that service with {@code firefly.decision.credit-report}, not here.
 */
@Component
@ConditionalOnProperty(name = "firefly.experience.credit-report.adapter", havingValue = "sandbox", matchIfMissing = true)
public class SandboxCreditReportAdapter implements CreditReportPort {

    public static final int EXAMPLE_CREDIT_SCORE = 640;

    @Override
    public Mono<CreditReportView> request(CreditReportQuery query) {
        String party = query.partyId() == null ? "anonymous" : query.partyId().toString();
        return Mono.just(new CreditReportView(
                "sandbox", "sandbox-" + party, EXAMPLE_CREDIT_SCORE, "ILLUSTRATIVE", true));
    }
}
