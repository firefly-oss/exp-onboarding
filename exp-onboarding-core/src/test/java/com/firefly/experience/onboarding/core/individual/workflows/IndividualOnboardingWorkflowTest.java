package com.firefly.experience.onboarding.core.individual.workflows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.domain.common.notifications.sdk.api.NotificationsApi;
import com.firefly.domain.kyc.kyb.sdk.api.KycApi;
import com.firefly.domain.people.sdk.api.CustomersApi;
import com.firefly.domain.people.sdk.model.RegisterAddressCommand;
import com.firefly.domain.people.sdk.model.UpdateCustomerCommand;
import com.firefly.experience.onboarding.core.individual.commands.SubmitPersonalDataCommand;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the BE-5a fields (maritalStatus + numberOfChildren) submitted via the
 * personal-data signal are propagated to the domain SDK call.
 */
@ExtendWith(MockitoExtension.class)
class IndividualOnboardingWorkflowTest {

    @Mock private CustomersApi customersApi;
    @Mock private KycApi kycApi;
    @Mock private NotificationsApi notificationsApi;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private IndividualOnboardingWorkflow workflow;

    private static final UUID PARTY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workflow = new IndividualOnboardingWorkflow(customersApi, kycApi, notificationsApi, objectMapper, validator);
    }

    @Test
    void receivePersonalData_propagatesMaritalStatusAndNumberOfChildrenToUpdateCustomer() {
        when(customersApi.addCustomerAddress(eq(PARTY_ID), any(RegisterAddressCommand.class), anyString()))
                .thenReturn(Mono.just(new Object()));
        when(customersApi.updateCustomer(any(UpdateCustomerCommand.class), anyString()))
                .thenReturn(Mono.just(new Object()));

        SubmitPersonalDataCommand cmd = SubmitPersonalDataCommand.builder()
                .addressLine1("Calle Mayor 1")
                .city("Madrid")
                .postalCode("28001")
                .maritalStatus("married")
                .numberOfChildren(3)
                .build();

        StepVerifier.create(workflow.receivePersonalData(PARTY_ID, cmd))
                .verifyComplete();

        ArgumentCaptor<UpdateCustomerCommand> captor = ArgumentCaptor.forClass(UpdateCustomerCommand.class);
        verify(customersApi).updateCustomer(captor.capture(), anyString());
        UpdateCustomerCommand sent = captor.getValue();
        assertThat(sent.getPartyId()).isEqualTo(PARTY_ID);
        assertThat(sent.getMaritalStatus()).isEqualTo(UpdateCustomerCommand.MaritalStatusEnum.MARRIED);
        assertThat(sent.getNumberOfChildren()).isEqualTo(3);
    }

    @Test
    void receivePersonalData_skipsUpdateCustomerWhenBe5aFieldsAbsent() {
        when(customersApi.addCustomerAddress(eq(PARTY_ID), any(RegisterAddressCommand.class), anyString()))
                .thenReturn(Mono.just(new Object()));

        SubmitPersonalDataCommand cmd = SubmitPersonalDataCommand.builder()
                .addressLine1("Calle Mayor 1")
                .city("Madrid")
                .postalCode("28001")
                .build();

        StepVerifier.create(workflow.receivePersonalData(PARTY_ID, cmd))
                .verifyComplete();

        verify(customersApi, never()).updateCustomer(any(UpdateCustomerCommand.class), anyString());
    }

    @Test
    void receivePersonalData_propagatesSeparatedMaritalStatusToUpdateCustomer() {
        // BE-5a (2026-05): SEPARATED is the fifth marital state added in V10. Verifies
        // the value flows from the experience signal payload through the domain SDK enum.
        when(customersApi.addCustomerAddress(eq(PARTY_ID), any(RegisterAddressCommand.class), anyString()))
                .thenReturn(Mono.just(new Object()));
        when(customersApi.updateCustomer(any(UpdateCustomerCommand.class), anyString()))
                .thenReturn(Mono.just(new Object()));

        SubmitPersonalDataCommand cmd = SubmitPersonalDataCommand.builder()
                .addressLine1("Calle Mayor 1")
                .city("Madrid")
                .postalCode("28001")
                .maritalStatus("SEPARATED")
                .build();

        StepVerifier.create(workflow.receivePersonalData(PARTY_ID, cmd))
                .verifyComplete();

        ArgumentCaptor<UpdateCustomerCommand> captor = ArgumentCaptor.forClass(UpdateCustomerCommand.class);
        verify(customersApi).updateCustomer(captor.capture(), anyString());
        assertThat(captor.getValue().getMaritalStatus()).isEqualTo(UpdateCustomerCommand.MaritalStatusEnum.SEPARATED);
    }

    @Test
    void receivePersonalData_throwsWhenMaritalStatusIsBlank() {
        // An empty maritalStatus string fails the @Pattern constraint on the
        // command DTO. The workflow must reject the signal payload before any
        // domain SDK call is made.
        SubmitPersonalDataCommand cmd = SubmitPersonalDataCommand.builder()
                .addressLine1("Calle Mayor 1")
                .city("Madrid")
                .postalCode("28001")
                .maritalStatus("")
                .build();

        StepVerifier.create(workflow.receivePersonalData(PARTY_ID, cmd))
                .consumeErrorWith(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) error;
                    assertThat(be.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("INVALID_SIGNAL_PAYLOAD");
                    assertThat(be.getMessage()).contains("maritalStatus");
                })
                .verify();

        verify(customersApi, never()).addCustomerAddress(eq(PARTY_ID), any(RegisterAddressCommand.class), anyString());
        verify(customersApi, never()).updateCustomer(any(UpdateCustomerCommand.class), anyString());
    }

    @Test
    void receiveIdentityDocuments_throwsWhenDocumentTypeIsBlank() {
        // documentType has @NotBlank — an empty value must be rejected at the
        // workflow boundary so no AttachEvidence call reaches the KYC API.
        // Validation is enforced synchronously in the step body before any Mono is built.
        com.firefly.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand cmd =
                com.firefly.experience.onboarding.core.individual.commands.SubmitIdentityDocumentsCommand.builder()
                        .documentType("")
                        .documentNumber("12345678Z")
                        .documentContent("base64-payload")
                        .mimeType("image/png")
                        .build();

        UUID caseId = UUID.randomUUID();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> workflow.receiveIdentityDocuments(caseId, PARTY_ID, cmd))
                .isInstanceOfSatisfying(BusinessException.class, be -> {
                    assertThat(be.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("INVALID_SIGNAL_PAYLOAD");
                    assertThat(be.getMessage()).contains("documentType");
                });
    }

    @Test
    void receivePersonalData_failsFastWhenMaritalStatusIsNotSupportedByDomainEnum() {
        // "ENGAGED" is rejected by the @Pattern constraint on the command DTO
        // before it ever reaches the toMaritalStatusEnum mapping, so no domain
        // SDK call is made.
        SubmitPersonalDataCommand cmd = SubmitPersonalDataCommand.builder()
                .addressLine1("Calle Mayor 1")
                .city("Madrid")
                .postalCode("28001")
                .maritalStatus("ENGAGED")
                .build();

        StepVerifier.create(workflow.receivePersonalData(PARTY_ID, cmd))
                .expectError(BusinessException.class)
                .verify();

        verify(customersApi, never()).addCustomerAddress(eq(PARTY_ID), any(RegisterAddressCommand.class), anyString());
        verify(customersApi, never()).updateCustomer(any(UpdateCustomerCommand.class), anyString());
    }
}
