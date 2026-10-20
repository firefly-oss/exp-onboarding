package io.fireflysoftware.experience.onboarding.web.openapi;

import org.fireflyframework.web.openapi.EnableOpenApiGen;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.ComponentScan;

@EnableOpenApiGen
@ComponentScan(basePackages = "io.fireflysoftware.experience.onboarding.web.controllers")
public class OpenApiGenApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenApiGenApplication.class, args);
    }
}
