package com.firefly.experience.onboarding.core.business.workflows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.domain.common.notifications.sdk.api.NotificationsApi;
import com.firefly.domain.kyc.kyb.sdk.api.KybApi;
import com.firefly.domain.kyc.kyb.sdk.model.RegisterUbosRequest;
import com.firefly.domain.kyc.kyb.sdk.model.SubmissionResult;
import com.firefly.domain.kyc.kyb.sdk.model.SubmitAuthorizedSignersRequest;
import com.firefly.domain.kyc.kyb.sdk.model.UboData;
import com.firefly.domain.people.sdk.api.BusinessesApi;
import com.firefly.domain.people.sdk.api.CustomersApi;
import com.firefly.domain.people.sdk.model.RegisterAddressCommand;
import com.firefly.domain.people.sdk.model.RegisterCustomerCommand;
import com.firefly.domain.people.sdk.model.UpdateBusinessCommand;

import java.util.Map;
import com.firefly.experience.onboarding.core.business.commands.SubmitAuthorizedSignersCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitCompanyDataCommand;
import com.firefly.experience.onboarding.core.business.commands.SubmitUbosCommand;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that BE-5b/c/d fields submitted via the business-onboarding signals are
 * propagated to the corresponding domain SDK calls.
 */
@ExtendWith(MockitoExtension.class)
class BusinessOnboardingWorkflowTest {

    @Mock private BusinessesApi businessesApi;
    @Mock private CustomersApi customersApi;
    @Mock private KybApi kybApi;
    @Mock private NotificationsApi notificationsApi;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private BusinessOnboardingWorkflow workflow;

    private static final UUID PARTY_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workflow = new BusinessOnboardingWorkflow(
                businessesApi, customersApi, kybApi, notificationsApi, objectMapper, validator);
    }

    @Test
    void receiveCompanyData_propagatesBe5bFieldsToUpdateBusinessCommand() {
        when(businessesApi.updateBusiness(any(UpdateBusinessCommand.class), anyString()))
                .thenReturn(Mono.just(new Object()));
        when(businessesApi.addBusinessAddress(eq(PARTY_ID), any(RegisterAddressCommand.class), anyString()))
                .thenReturn(Mono.just(new Object()));

        SubmitCompanyDataCommand cmd = SubmitCompanyDataCommand.builder()
                .legalName("Acme Corp SL")
                .taxId("B12345678")
                .addressLine1("Gran Via 1")
                .city("Madrid")
                .postalCode("28013")
                .numberOfEmployees("51-250")
                .annualRevenue(new BigDecimal("1500000.00"))
                .cnaeCode("6201")
                .contactName("Jane Doe")
                .contactPosition("CFO")
                .contactEmail("jane@acme.example")
                .contactPhone("+34911234567")
                .build();

        StepVerifier.create(workflow.receiveCompanyData(PARTY_ID, cmd))
                .verifyComplete();

        ArgumentCaptor<UpdateBusinessCommand> captor = ArgumentCaptor.forClass(UpdateBusinessCommand.class);
        verify(businessesApi).updateBusiness(captor.capture(), anyString());
        UpdateBusinessCommand sent = captor.getValue();
        assertThat(sent.getEmployeeRange()).isEqualTo("51-250");
        assertThat(sent.getAnnualRevenue()).isEqualByComparingTo(new BigDecimal("1500000.00"));
        assertThat(sent.getCnaeCode()).isEqualTo("6201");
        assertThat(sent.getContactName()).isEqualTo("Jane Doe");
        assertThat(sent.getContactPosition()).isEqualTo("CFO");
        assertThat(sent.getContactEmail()).isEqualTo("jane@acme.example");
        assertThat(sent.getContactPhone()).isEqualTo("+34911234567");
    }

    @Test
    void receiveUbos_resolvesNaturalPersonIdAndPropagatesEmailAndOwnershipType() {
        UUID firstNaturalPersonId = UUID.randomUUID();
        UUID secondNaturalPersonId = UUID.randomUUID();
        when(customersApi.registerCustomer(any(RegisterCustomerCommand.class), anyString()))
                .thenReturn(Mono.just((Object) Map.of("partyId", firstNaturalPersonId.toString())))
                .thenReturn(Mono.just((Object) Map.of("partyId", secondNaturalPersonId.toString())));
        when(kybApi.registerUbos(eq(CASE_ID), any(RegisterUbosRequest.class), anyString()))
                .thenReturn(Mono.just(new SubmissionResult()));

        SubmitUbosCommand cmd = SubmitUbosCommand.builder()
                .ubos(List.of(
                        SubmitUbosCommand.UboEntry.builder()
                                .firstName("John")
                                .lastName("Doe")
                                .documentType("DNI")
                                .documentNumber("11111111H")
                                .ownershipPercentage(new BigDecimal("60"))
                                .email("john@acme.example")
                                .ownershipType("indirect")
                                .build(),
                        SubmitUbosCommand.UboEntry.builder()
                                .firstName("Mary")
                                .lastName("Roe")
                                .documentType("DNI")
                                .documentNumber("22222222P")
                                .ownershipPercentage(new BigDecimal("40"))
                                .email("mary@acme.example")
                                // no ownershipType — should default to DIRECT
                                .build()))
                .build();

        StepVerifier.create(workflow.receiveUbos(PARTY_ID, CASE_ID, cmd))
                .verifyComplete();

        // BE-5d: each UBO must be registered as a natural person before being
        // attached to the KYB case so the core schema's NOT NULL naturalPersonId
        // constraint is satisfied. The UBO's documentNumber must propagate to the
        // natural-person record's taxIdNumber for SEPBLAC traceability.
        ArgumentCaptor<RegisterCustomerCommand> registerCaptor =
                ArgumentCaptor.forClass(RegisterCustomerCommand.class);
        verify(customersApi, org.mockito.Mockito.times(2))
                .registerCustomer(registerCaptor.capture(), anyString());
        List<RegisterCustomerCommand> registered = registerCaptor.getAllValues();
        assertThat(registered.get(0).getNaturalPerson().getGivenName()).isEqualTo("John");
        assertThat(registered.get(0).getNaturalPerson().getFamilyName1()).isEqualTo("Doe");
        assertThat(registered.get(0).getNaturalPerson().getTaxIdNumber()).isEqualTo("11111111H");
        assertThat(registered.get(1).getNaturalPerson().getGivenName()).isEqualTo("Mary");
        assertThat(registered.get(1).getNaturalPerson().getFamilyName1()).isEqualTo("Roe");
        assertThat(registered.get(1).getNaturalPerson().getTaxIdNumber()).isEqualTo("22222222P");

        ArgumentCaptor<RegisterUbosRequest> captor = ArgumentCaptor.forClass(RegisterUbosRequest.class);
        verify(kybApi).registerUbos(eq(CASE_ID), captor.capture(), anyString());
        List<UboData> sent = captor.getValue().getUbos();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).getNaturalPersonId()).isEqualTo(firstNaturalPersonId);
        assertThat(sent.get(0).getEmail()).isEqualTo("john@acme.example");
        assertThat(sent.get(0).getOwnershipType()).isEqualTo("INDIRECT");
        assertThat(sent.get(1).getNaturalPersonId()).isEqualTo(secondNaturalPersonId);
        assertThat(sent.get(1).getEmail()).isEqualTo("mary@acme.example");
        assertThat(sent.get(1).getOwnershipType()).isEqualTo("DIRECT");
    }

    @Test
    void receiveAuthorizedSigners_registersAttorneyAndPropagatesBe5cFields() {
        UUID attorneyId = UUID.randomUUID();
        when(customersApi.registerCustomer(any(RegisterCustomerCommand.class), anyString()))
                .thenReturn(Mono.just((Object) Map.of("partyId", attorneyId.toString())));
        when(kybApi.submitAuthorizedSigners(eq(CASE_ID), any(SubmitAuthorizedSignersRequest.class), anyString()))
                .thenReturn(Mono.just(new SubmissionResult()));

        SubmitAuthorizedSignersCommand cmd = SubmitAuthorizedSignersCommand.builder()
                .signers(List.of(SubmitAuthorizedSignersCommand.SignerEntry.builder()
                        .firstName("Jane")
                        .lastName("Smith")
                        .documentType("DNI")
                        .documentNumber("12345678Z")
                        .role("LEGAL_REPRESENTATIVE")
                        .powerDocumentReference("doc-ref-1")
                        .email("jane.smith@acme.example")
                        .signingAuthorized(true)
                        .isPep(false)
                        .build()))
                .build();

        StepVerifier.create(workflow.receiveAuthorizedSigners(PARTY_ID, CASE_ID, cmd))
                .verifyComplete();

        // BE-5c: the signer's documentNumber must propagate to the natural-person
        // record's taxIdNumber so the customer row carries a document-traceable
        // identity (SEPBLAC requirement). The full document type and number remain
        // on the SignerData power-of-attorney record.
        ArgumentCaptor<RegisterCustomerCommand> registerCaptor =
                ArgumentCaptor.forClass(RegisterCustomerCommand.class);
        verify(customersApi).registerCustomer(registerCaptor.capture(), anyString());
        RegisterCustomerCommand registered = registerCaptor.getValue();
        assertThat(registered.getNaturalPerson().getGivenName()).isEqualTo("Jane");
        assertThat(registered.getNaturalPerson().getFamilyName1()).isEqualTo("Smith");
        assertThat(registered.getNaturalPerson().getTaxIdNumber()).isEqualTo("12345678Z");

        ArgumentCaptor<SubmitAuthorizedSignersRequest> captor =
                ArgumentCaptor.forClass(SubmitAuthorizedSignersRequest.class);
        verify(kybApi).submitAuthorizedSigners(eq(CASE_ID), captor.capture(), anyString());
        SubmitAuthorizedSignersRequest sent = captor.getValue();
        assertThat(sent.getPartyId()).isEqualTo(PARTY_ID);
        assertThat(sent.getSigners()).hasSize(1);
        assertThat(sent.getSigners().get(0).getAttorneyId()).isEqualTo(attorneyId);
        assertThat(sent.getSigners().get(0).getFirstName()).isEqualTo("Jane");
        assertThat(sent.getSigners().get(0).getLastName()).isEqualTo("Smith");
        assertThat(sent.getSigners().get(0).getDocumentType()).isEqualTo("DNI");
        assertThat(sent.getSigners().get(0).getDocumentNumber()).isEqualTo("12345678Z");
        assertThat(sent.getSigners().get(0).getRole()).isEqualTo("LEGAL_REPRESENTATIVE");
        assertThat(sent.getSigners().get(0).getPowerDocumentReference()).isEqualTo("doc-ref-1");
        assertThat(sent.getSigners().get(0).getEmail()).isEqualTo("jane.smith@acme.example");
        assertThat(sent.getSigners().get(0).getSigningAuthorized()).isTrue();
        assertThat(sent.getSigners().get(0).getIsPep()).isFalse();
    }

    @Test
    void receiveCompanyData_throwsWhenSignalPayloadHasInvalidEmail() {
        // Malformed contactEmail must be rejected at the workflow boundary so no
        // updateBusiness call reaches the domain layer. Validation is enforced
        // synchronously in the step body before any Mono is constructed.
        SubmitCompanyDataCommand cmd = SubmitCompanyDataCommand.builder()
                .legalName("Acme Corp SL")
                .taxId("B12345678")
                .addressLine1("Gran Via 1")
                .city("Madrid")
                .postalCode("28013")
                .contactEmail("not-an-email")
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflow.receiveCompanyData(PARTY_ID, cmd))
                .isInstanceOfSatisfying(BusinessException.class, be -> {
                    assertThat(be.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("INVALID_SIGNAL_PAYLOAD");
                    assertThat(be.getMessage()).contains("contactEmail");
                });

        verify(businessesApi, org.mockito.Mockito.never())
                .updateBusiness(any(UpdateBusinessCommand.class), anyString());
    }

    @Test
    void receiveUbos_throwsWhenUboHasInvalidEmail() {
        SubmitUbosCommand cmd = SubmitUbosCommand.builder()
                .ubos(List.of(SubmitUbosCommand.UboEntry.builder()
                        .firstName("John")
                        .lastName("Doe")
                        .documentType("DNI")
                        .documentNumber("11111111H")
                        .ownershipPercentage(new BigDecimal("60"))
                        .email("definitely-not-an-email")
                        .ownershipType("DIRECT")
                        .build()))
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflow.receiveUbos(PARTY_ID, CASE_ID, cmd))
                .isInstanceOfSatisfying(BusinessException.class, be -> {
                    assertThat(be.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("INVALID_SIGNAL_PAYLOAD");
                    assertThat(be.getMessage()).contains("email");
                });

        verify(customersApi, org.mockito.Mockito.never())
                .registerCustomer(any(RegisterCustomerCommand.class), anyString());
        verify(kybApi, org.mockito.Mockito.never())
                .registerUbos(any(UUID.class), any(RegisterUbosRequest.class), anyString());
    }
}
