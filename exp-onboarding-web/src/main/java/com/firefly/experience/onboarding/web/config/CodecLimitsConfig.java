package com.firefly.experience.onboarding.web.config;

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
