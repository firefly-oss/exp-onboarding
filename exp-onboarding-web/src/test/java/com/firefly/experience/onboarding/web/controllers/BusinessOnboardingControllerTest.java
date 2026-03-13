package com.firefly.experience.onboarding.web.controllers;

import com.firefly.experience.onboarding.core.business.commands.InitiateBusinessOnboardingCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCorporateDocumentsCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitUbosCommand;
import com.firefly.experience.onboarding.core.business.commands.UpdatePartialDataCommand;
import com.firefly.experience.onboarding.core.business.queries.BusinessOnboardingStatusDTO;
import com.firefly.experience.onboarding.core.business.queries.KybStatusDTO;
import com.firefly.experience.onboarding.core.business.services.BusinessOnboardingService;
import com.firefly.experience.onboarding.core.business.workflows.BusinessOnboardingWorkflow;
import com.firefly.experience.onboarding.web.dto.BusinessOnboardingResponse;
import com.firefly.experience.onboarding.web.dto.KybStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessOnboardingControllerTest {

    @Mock
    private BusinessOnboardingService onboardingService;

    @InjectMocks
    private BusinessOnboardingController controller;

    private static final UUID ONBOARDING_ID = UUID.randomUUID();
    private static final UUID PARTY_ID = UUID.randomUUID();
    private static final UUID KYB_CASE_ID = UUID.randomUUID();

    /**
     * Service-layer DTO returned by mocked service methods — the controller maps this
     * to {@link BusinessOnboardingResponse} before returning it to the client.
     */
    private static final BusinessOnboardingStatusDTO MOCK_STATUS = BusinessOnboardingStatusDTO.builder()
            .onboardingId(ONBOARDING_ID)
            .partyId(PARTY_ID)
            .kybCaseId(KYB_CASE_ID)
            .currentPhase(BusinessOnboardingWorkflow.PHASE_INITIATED)
            .completedSteps(List.of(
                    BusinessOnboardingWorkflow.STEP_REGISTER_PARTY,
                    BusinessOnboardingWorkflow.STEP_OPEN_KYB_CASE,
                    BusinessOnboardingWorkflow.STEP_SEND_WELCOME))
            .nextStep(BusinessOnboardingWorkflow.STEP_RECEIVE_COMPANY_DATA)
            .kybStatus("CASE_OPENED")
            .build();

    @Test
    void initiateOnboarding_returnsCreatedWithBusinessOnboardingResponse() {
        when(onboardingService.initiateOnboarding(any(InitiateBusinessOnboardingCommand.class)))
                .thenReturn(Mono.just(MOCK_STATUS));

        InitiateBusinessOnboardingCommand command = InitiateBusinessOnboardingCommand.builder()
                .businessName("Acme Corp")
                .registrationNumber("B12345678")
                .countryOfIncorporation("ES")
                .build();

        StepVerifier.create(controller.initiateOnboarding(command))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    BusinessOnboardingResponse body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getOnboardingId()).isEqualTo(ONBOARDING_ID);
                    assertThat(body.getPartyId()).isEqualTo(PARTY_ID);
                    assertThat(body.getCurrentPhase())
                            .isEqualTo(BusinessOnboardingWorkflow.PHASE_INITIATED);
                })
                .verifyComplete();
    }

    @Test
    void getOnboardingStatus_returnsOkWithBusinessOnboardingResponse() {
        when(onboardingService.getStatus(ONBOARDING_ID))
                .thenReturn(Mono.just(MOCK_STATUS));

        StepVerifier.create(controller.getOnboardingStatus(ONBOARDING_ID))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    BusinessOnboardingResponse body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCurrentPhase())
                            .isEqualTo(BusinessOnboardingWorkflow.PHASE_INITIATED);
                    assertThat(body.getCompletedSteps())
                            .contains(BusinessOnboardingWorkflow.STEP_REGISTER_PARTY);
                    assertThat(body.getNextStep())
                            .isEqualTo(BusinessOnboardingWorkflow.STEP_RECEIVE_COMPANY_DATA);
                })
                .verifyComplete();
    }

    @Test
    void updatePartialData_returnsOkWithStatusAcknowledgement() {
        when(onboardingService.updatePartialData(eq(ONBOARDING_ID), any(UpdatePartialDataCommand.class)))
                .thenReturn(Mono.empty());

        UpdatePartialDataCommand command = UpdatePartialDataCommand.builder()
                .businessName("Acme Corp Updated SL")
                .contactEmail("ceo@acmecorp.es")
                .build();

        StepVerifier.create(controller.updatePartialData(ONBOARDING_ID, command))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("onboardingId")).isEqualTo(ONBOARDING_ID);
                    assertThat(body.get("status")).isEqualTo("PARTIAL_DATA_UPDATED");
                })
                .verifyComplete();
    }

    @Test
    void submitCompanyData_returnsOkWithStatusAcknowledgement() {
        when(onboardingService.submitCompanyData(eq(ONBOARDING_ID), any(SubmitCompanyDataCommand.class)))
                .thenReturn(Mono.empty());

        SubmitCompanyDataCommand command = SubmitCompanyDataCommand.builder()
                .legalName("Acme Corp SL")
                .taxId("B12345678")
                .build();

        StepVerifier.create(controller.submitCompanyData(ONBOARDING_ID, command))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("onboardingId")).isEqualTo(ONBOARDING_ID);
                    assertThat(body.get("status")).isEqualTo("COMPANY_DATA_SUBMITTED");
                })
                .verifyComplete();
    }

    @Test
    void submitUbos_returnsOkWithStatusAcknowledgement() {
        when(onboardingService.submitUbos(eq(ONBOARDING_ID), any(SubmitUbosCommand.class)))
                .thenReturn(Mono.empty());

        SubmitUbosCommand command = SubmitUbosCommand.builder()
                .ubos(List.of(SubmitUbosCommand.UboEntry.builder()
                        .firstName("John").lastName("Doe")
                        .ownershipPercentage(new BigDecimal("51"))
                        .build()))
                .build();

        StepVerifier.create(controller.submitUbos(ONBOARDING_ID, command))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("status")).isEqualTo("UBOS_SUBMITTED");
                })
                .verifyComplete();
    }

    @Test
    void submitCorporateDocuments_returnsOkWithStatusAcknowledgement() {
        when(onboardingService.submitCorporateDocuments(eq(ONBOARDING_ID), any(SubmitCorporateDocumentsCommand.class)))
                .thenReturn(Mono.empty());

        SubmitCorporateDocumentsCommand command = SubmitCorporateDocumentsCommand.builder()
                .documents(List.of(SubmitCorporateDocumentsCommand.CorporateDocumentEntry.builder()
                        .documentType("ARTICLES_OF_INCORPORATION")
                        .documentReference("doc-ref-123")
                        .build()))
                .build();

        StepVerifier.create(controller.submitCorporateDocuments(ONBOARDING_ID, command))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("status")).isEqualTo("CORPORATE_DOCUMENTS_SUBMITTED");
                })
                .verifyComplete();
    }

    @Test
    void submitAuthorizedSigners_returnsOkWithStatusAcknowledgement() {
        when(onboardingService.submitAuthorizedSigners(eq(ONBOARDING_ID), any(SubmitAuthorizedSignersCommand.class)))
                .thenReturn(Mono.empty());

        SubmitAuthorizedSignersCommand command = SubmitAuthorizedSignersCommand.builder()
                .signers(List.of(SubmitAuthorizedSignersCommand.SignerEntry.builder()
                        .firstName("Jane").lastName("Smith")
                        .role("LEGAL_REPRESENTATIVE")
                        .build()))
                .build();

        StepVerifier.create(controller.submitAuthorizedSigners(ONBOARDING_ID, command))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("status")).isEqualTo("AUTHORIZED_SIGNERS_SUBMITTED");
                })
                .verifyComplete();
    }

    @Test
    void triggerKybVerification_returnsAccepted() {
        when(onboardingService.triggerKybVerification(ONBOARDING_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(controller.triggerKybVerification(ONBOARDING_ID))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("status")).isEqualTo("KYB_TRIGGERED");
                })
                .verifyComplete();
    }

    @Test
    void getKybStatus_returnsOkWithKybStatusResponse() {
        KybStatusDTO dto = KybStatusDTO.builder()
                .caseId(KYB_CASE_ID)
                .status("IN_PROGRESS")
                .build();

        when(onboardingService.getKybStatus(ONBOARDING_ID))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(controller.getKybStatus(ONBOARDING_ID))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    KybStatusResponse body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getCaseId()).isEqualTo(KYB_CASE_ID);
                    assertThat(body.getStatus()).isEqualTo("IN_PROGRESS");
                })
                .verifyComplete();
    }

    @Test
    void completeOnboarding_returnsOkWithStatusAcknowledgement() {
        when(onboardingService.completeOnboarding(ONBOARDING_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(controller.completeOnboarding(ONBOARDING_ID))
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
