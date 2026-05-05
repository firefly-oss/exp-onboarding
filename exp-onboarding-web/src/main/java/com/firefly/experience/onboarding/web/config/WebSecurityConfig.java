/*
 * Copyright 2025 Firefly Software Solutions Inc
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

package com.firefly.experience.onboarding.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive web security configuration for the Experience Onboarding service.
 * <p>
 * Disables Spring Security's default HTTP Basic Auth (which is auto-activated by the
 * presence of {@code spring-boot-starter-security} brought in transitively by
 * {@code fireflyframework-starter-application}) and exposes Swagger/OpenAPI and
 * Actuator probes as public endpoints.
 * <p>
 * Method-level authorization is delegated to the Firefly Framework's
 * {@code SecurityAspect} (AOP) driven by the {@code @Secure} annotation and the
 * {@code EndpointSecurityRegistry}; therefore the HTTP filter chain itself permits
 * all exchanges and only acts as a gatekeeper to disable the noisy defaults
 * (Basic Auth, form login, CSRF) that do not apply to a stateless REST API.
 */
@Configuration
@EnableWebFluxSecurity
public class WebSecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus"
                        ).permitAll()
                        .anyExchange().permitAll()
                )
                .build();
    }
}
