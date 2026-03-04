package com.firefly.experience.onboarding.web.controllers;

import com.firefly.experience.onboarding.core.individual.commands.InitiateOnboardingCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import com.firefly.experience.onboarding.core.individual.queries.JourneyStatusDTO;
import com.firefly.experience.onboarding.core.individual.queries.KycStatusDTO;
import com.firefly.experience.onboarding.core.individual.services.IndividualOnboardingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndividualOnboardingControllerTest {

    @Mock
    private IndividualOnboardingService onboardingService;

    @InjectMocks
    private IndividualOnboardingController controller;

    @Test
    void initiateOnboarding_returnsCreatedWithOnboardingId() {
        UUID onboardingId = UUID.randomUUID();

        when(onboardingService.initiateOnboarding(any(InitiateOnboardingCommand.class)))
                .thenReturn(Mono.just(onboardingId));

        InitiateOnboardingCommand command = InitiateOnboardingCommand.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();

        StepVerifier.create(controller.initiateOnboarding(command))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("onboardingId")).isEqualTo(onboardingId);
                    assertThat(body.get("status")).isEqualTo("INITIATED");
                })
                .verifyComplete();
    }

    @Test
    void getOnboardingStatus_returnsJourneyStatus() {
        UUID onboardingId = UUID.randomUUID();
        UUID kycCaseId = UUID.randomUUID();

        JourneyStatusDTO dto = JourneyStatusDTO.builder()
                .partyId(onboardingId)
                .kycCaseId(kycCaseId)
                .currentPhase("AWAITING_PERSONAL_DATA")
                .completedSteps(List.of("register-party", "open-kyc-case", "send-welcome"))
                .nextStep("receive-personal-data")
                .build();

        when(onboardingService.getJourneyStatus(onboardingId))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(controller.getOnboardingStatus(onboardingId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getCurrentPhase())
                            .isEqualTo("AWAITING_PERSONAL_DATA");
                    assertThat(response.getBody().getCompletedSteps())
                            .contains("register-party", "open-kyc-case");
                    assertThat(response.getBody().getNextStep())
                            .isEqualTo("receive-personal-data");
                })
                .verifyComplete();
    }

    @Test
    void submitPersonalData_returnsOkWithStatus() {
        UUID onboardingId = UUID.randomUUID();

        when(onboardingService.submitPersonalData(eq(onboardingId), any(SubmitPersonalDataCommand.class)))
                .thenReturn(Mono.empty());

        SubmitPersonalDataCommand command = SubmitPersonalDataCommand.builder()
                .dateOfBirth("1990-01-15")
                .nationality("ES")
                .addressLine1("Calle Mayor 1")
                .city("Madrid")
                .postalCode("28001")
                .country("ES")
                .build();

        StepVerifier.create(controller.submitPersonalData(onboardingId, command))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("status")).isEqualTo("PERSONAL_DATA_SUBMITTED");
                })
                .verifyComplete();
    }

    @Test
    void submitIdentityDocuments_returnsOkWithStatus() {
        UUID onboardingId = UUID.randomUUID();

        when(onboardingService.submitIdentityDocuments(eq(onboardingId), any(SubmitIdentityDocumentsCommand.class)))
                .thenReturn(Mono.empty());

        SubmitIdentityDocumentsCommand command = SubmitIdentityDocumentsCommand.builder()
                .documentType("PASSPORT")
                .documentNumber("AB123456")
                .build();

        StepVerifier.create(controller.submitIdentityDocuments(onboardingId, command))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("status")).isEqualTo("DOCUMENTS_SUBMITTED");
                })
                .verifyComplete();
    }

    @Test
    void triggerKyc_returnsAccepted() {
        UUID onboardingId = UUID.randomUUID();

        when(onboardingService.triggerKyc(onboardingId))
                .thenReturn(Mono.empty());

        StepVerifier.create(controller.triggerKyc(onboardingId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("status")).isEqualTo("KYC_TRIGGERED");
                })
                .verifyComplete();
    }

    @Test
    void getKycStatus_returnsKycStatusDto() {
        UUID onboardingId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        KycStatusDTO dto = KycStatusDTO.builder()
                .caseId(caseId)
                .status("PENDING")
                .build();

        when(onboardingService.getKycStatus(onboardingId))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(controller.getKycStatus(onboardingId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getCaseId()).isEqualTo(caseId);
                    assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }

    @Test
    void completeOnboarding_returnsOkWithStatus() {
        UUID onboardingId = UUID.randomUUID();

        when(onboardingService.completeOnboarding(onboardingId))
                .thenReturn(Mono.empty());

        StepVerifier.create(controller.completeOnboarding(onboardingId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("status")).isEqualTo("COMPLETED");
                })
                .verifyComplete();
    }
}
