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

import io.fireflysoftware.domain.referencedata.sdk.api.CatalogsApi;
import io.fireflysoftware.domain.referencedata.sdk.invoker.ApiClient;
import io.fireflysoftware.experience.onboarding.core.ports.ProductFamilyView;
import io.fireflysoftware.experience.onboarding.core.ports.ReferenceCatalogPort;
import io.fireflysoftware.experience.onboarding.core.ports.Regulation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Reads one product-family catalog row from domain-core-reference-data.
 */
@Component
public class HttpReferenceCatalogAdapter implements ReferenceCatalogPort {

    private final CatalogsApi catalogs;

    @Autowired
    public HttpReferenceCatalogAdapter(
            @Value("${api-configuration.domain-platform.reference-data.base-path:http://localhost:8089}") String basePath) {
        ApiClient client = new ApiClient();
        client.setBasePath(basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath);
        this(new CatalogsApi(client));
    }

    HttpReferenceCatalogAdapter(CatalogsApi catalogs) {
        this.catalogs = catalogs;
    }

    @Override
    public Mono<ProductFamilyView> findProductFamily(String code) {
        return catalogs.findCatalogEntry("PRODUCT_FAMILY", code)
                .map(row -> {
                    Map<String, String> attributes = row.getAttributes() == null ? Map.of() : row.getAttributes();
                    String industry = attributes.get("industry");
                    String familyCode = row.getCode() == null || row.getCode().isBlank() ? code : row.getCode();
                    return new ProductFamilyView(
                            familyCode,
                            Regulation.valueOf(String.valueOf(attributes.get("regulation"))),
                            industry == null || industry.isBlank() ? null : industry);
                });
    }
}
