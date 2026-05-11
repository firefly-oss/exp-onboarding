package com.firefly.experience.onboarding.infra;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * WebClient configured for the business-intelligence domain service.
 * The SDK module for that service is not generated locally, so we call its
 * REST endpoints directly via WebClient instead of through a generated client.
 */
@Configuration
public class BusinessIntelligenceClientFactory {

    public static final String BEAN_NAME = "businessIntelligenceWebClient";

    @Bean(BEAN_NAME)
    public WebClient businessIntelligenceWebClient(BusinessIntelligenceProperties properties) {
        long timeoutMs = properties.getTimeout().toMillis();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMs)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(properties.getBasePath())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
