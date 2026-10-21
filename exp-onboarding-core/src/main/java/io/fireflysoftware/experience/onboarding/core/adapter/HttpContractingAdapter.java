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

import io.fireflysoftware.domain.common.contracts.sdk.api.ContractsApi;
import io.fireflysoftware.domain.common.contracts.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.common.contracts.sdk.model.CreateContractCommand;
import io.fireflysoftware.experience.onboarding.core.ports.ContractingPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Opens an agreement through domain-common-contracts. Quote, bind, and claim stay on their own domains.
 */
@Component
public class HttpContractingAdapter implements ContractingPort {

    private final ContractsApi contracts;

    @Autowired
    public HttpContractingAdapter(
            @Value("${api-configuration.domain-platform.common-contracts.base-path:http://localhost:8090}") String basePath) {
        ApiClient client = new ApiClient();
        client.setBasePath(trim(basePath));
        this.contracts = new ContractsApi(client);
    }

    HttpContractingAdapter(ContractsApi contracts) {
        this.contracts = contracts;
    }

    @Override
    public Mono<UUID> open(UUID partyId, String contractType, String description) {
        CreateContractCommand command = new CreateContractCommand()
                .partyId(partyId)
                .contractType(contractType)
                .description(description);
        return contracts.createContract(command, UUID.randomUUID().toString()).map(HttpContractingAdapter::contractId);
    }

    private static UUID contractId(Object body) {
        if (body instanceof UUID id) {
            return id;
        }
        if (body instanceof String text) {
            return UUID.fromString(text.replace("\"", "").trim());
        }
        if (body instanceof Map<?, ?> map) {
            Object id = map.get("contractId");
            if (id == null) {
                id = map.get("id");
            }
            if (id != null) {
                return UUID.fromString(String.valueOf(id));
            }
        }
        throw new IllegalStateException("domain-common-contracts did not return a contract id");
    }

    private static String trim(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("contracts base path is not set");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
