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


package io.fireflysoftware.experience.onboarding.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import org.springframework.util.unit.DataSize;

/**
 * Raises the WebFlux inbound JSON buffer cap so the BFF can accept large
 * payloads (notably the base64-encoded identity documents). Composed alongside
 * the framework's WebFluxConfig so the Jackson encoder/decoder stay intact —
 * we only override the codec's in-memory size.
 */
@Configuration
public class CodecLimitsConfig implements WebFluxConfigurer {

    private final int maxInMemorySize;

    public CodecLimitsConfig(@Value("${spring.codec.max-in-memory-size:20MB}") DataSize maxInMemorySize) {
        this.maxInMemorySize = (int) maxInMemorySize.toBytes();
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(maxInMemorySize);
    }
}
