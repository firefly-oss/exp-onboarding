package com.firefly.experience.onboarding.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * Spring Boot application entry point for the Experience Onboarding service.
 * <p>
 * Provides REST APIs for individual and business onboarding journeys,
 * orchestrating domain-tier calls to customer-people, KYC/KYB, and notifications services.
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.firefly.experience.onboarding",
                "org.fireflyframework.web"
        }
)
@EnableWebFlux
@ConfigurationPropertiesScan
@OpenAPIDefinition(
        info = @Info(
                title = "${spring.application.name}",
                version = "${spring.application.version}",
                description = "Experience layer API for individual and business onboarding journeys",
                contact = @Contact(
                        name = "${spring.application.team.name}",
                        email = "${spring.application.team.email}"
                )
        ),
        servers = {
                @Server(
                        url = "http://core.getfirefly.io/exp-onboarding",
                        description = "Development Environment"
                ),
                @Server(
                        url = "/",
                        description = "Local Development Environment"
                )
        }
)
public class ExpOnboardingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExpOnboardingApplication.class, args);
    }
}
