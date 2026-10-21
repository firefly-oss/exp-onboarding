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

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Raised when {@link DecisionPort} returns {@link DecisionOutcome#DECLINE}.
 * The domain command is not called.
 */
public class DecisionDeclinedException extends BusinessException {

    public DecisionDeclinedException(List<String> reasonCodes) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "DECISION_DECLINED",
                reasonCodes == null || reasonCodes.isEmpty()
                        ? "Decision declined"
                        : String.join(",", reasonCodes));
    }
}
