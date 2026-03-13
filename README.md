# exp-onboarding

> Backend-for-Frontend service that orchestrates individual and business customer onboarding journeys through signal-driven workflows

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Module Structure](#module-structure)
- [Functional Verticals](#functional-verticals)
- [API Endpoints](#api-endpoints)
- [Domain SDK Dependencies](#domain-sdk-dependencies)
- [Configuration](#configuration)
- [Running Locally](#running-locally)
- [Testing](#testing)

## Overview

`exp-onboarding` is the experience-layer service that guides new customers through the onboarding lifecycle. It exposes two independent journey APIs — one for individual (natural person) onboarding and one for business (legal entity) onboarding — each built on the Firefly signal-driven workflow engine.

Unlike simple composition services that forward requests to domain SDKs and return, `exp-onboarding` models each journey as a long-running `@Workflow` that executes steps automatically until it reaches a `@WaitForSignal` gate. The workflow then pauses and persists its full execution state to Redis. When the frontend calls the next atomic endpoint, the service delivers a named signal to the workflow engine, which resumes execution from the checkpoint, runs the next group of steps against downstream domain SDKs, and pauses again at the following gate.

This architecture provides durable execution across process restarts, step-level observability via `@WorkflowQuery`, automatic compensation if a later step fails, and a clean recovery path: if the user abandons the journey and returns later, the frontend calls the status endpoint, reads `currentPhase` and `nextStep`, and navigates directly to the correct form without re-submitting any data.

## Architecture

```
Frontend / Mobile App
         |
         v
exp-onboarding  (port 8096)
         |
         +---> WorkflowEngine / SignalService / WorkflowQueryService
         |             |
         |    ┌─────────┴──────────┐
         |    v                    v
         |  IndividualOnboarding  BusinessOnboarding
         |    Workflow              Workflow
         |
         +---> domain-customer-people-sdk   (CustomersApi, BusinessesApi)
         |
         +---> domain-customer-kyc-kyb-sdk  (KycApi, KybApi)
         |
         +---> domain-common-notifications-sdk  (NotificationsApi)
```

Workflow state is persisted to Redis with key prefix `exp-onboarding:{correlationId}`, a TTL of 72 hours, and a retention period of 30 days for completed journeys.

## Module Structure

| Module | Purpose |
|--------|---------|
| `exp-onboarding-interfaces` | Reserved for future shared contracts |
| `exp-onboarding-core` | Workflow definitions (`IndividualOnboardingWorkflow`, `BusinessOnboardingWorkflow`), service interfaces and implementations, command DTOs per atomic endpoint, query DTOs (`JourneyStatusDTO`, `BusinessOnboardingStatusDTO`) |
| `exp-onboarding-infra` | `CustomerPeopleClientFactory` (CustomersApi, BusinessesApi), `CustomerKycKybClientFactory` (KycApi, KybApi), `CommonNotificationsClientFactory` (NotificationsApi), and their `@ConfigurationProperties` |
| `exp-onboarding-web` | `IndividualOnboardingController`, `BusinessOnboardingController`, Spring Boot application class, `application.yaml` |
| `exp-onboarding-sdk` | Auto-generated reactive SDK from the OpenAPI spec |

## Functional Verticals

| Vertical | Controller | Endpoints | Description |
|----------|-----------|-----------|-------------|
| Individual Onboarding | `IndividualOnboardingController` | 7 | Signal-driven `@Workflow` (24 h timeout) guiding a natural person from registration through KYC to activation |
| Business Onboarding | `BusinessOnboardingController` | 9 | Signal-driven `@Workflow` (48 h timeout) guiding a legal entity from registration through KYB to activation |

## API Endpoints

### Individual Onboarding

Base path: `/api/v1/onboarding/individuals`

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/v1/onboarding/individuals` | Initiate onboarding — registers the party, opens a KYC case, sends a welcome notification; returns `onboardingId` | `201 Created` |
| `GET` | `/api/v1/onboarding/individuals/{onboardingId}` | Get journey status — returns `currentPhase`, `completedSteps`, `nextStep`, and `kycVerificationStatus` | `200 OK` |
| `POST` | `/api/v1/onboarding/individuals/{onboardingId}/personal-data` | Submit personal data — delivers `personal-data-submitted` signal with address and personal details; advances past the personal-data gate | `200 OK` |
| `POST` | `/api/v1/onboarding/individuals/{onboardingId}/identity-documents` | Submit identity documents — delivers `identity-docs-submitted` signal with document metadata; attaches evidence to the KYC case | `200 OK` |
| `POST` | `/api/v1/onboarding/individuals/{onboardingId}/kyc` | Trigger KYC verification — delivers `kyc-triggered` signal; starts the verification process via the KYC/KYB domain service | `202 Accepted` |
| `GET` | `/api/v1/onboarding/individuals/{onboardingId}/kyc/status` | Get KYC status — retrieves the current verification status from the KYC case | `200 OK` |
| `POST` | `/api/v1/onboarding/individuals/{onboardingId}/completion` | Complete onboarding — delivers `completion-requested` signal; verifies KYC approval, activates the party, sends a completion notification | `200 OK` |

**Individual workflow signal gates:**

```
Layer 0:  [register-party]                              ← compensatable
Layer 1:  [open-kyc-case] [send-welcome]                ← parallel; KYC case compensatable
Layer 2:  [receive-personal-data]                       ← @WaitForSignal("personal-data-submitted")
Layer 3:  [receive-identity-docs]                       ← @WaitForSignal("identity-docs-submitted")
Layer 4:  [trigger-kyc-verification]                    ← @WaitForSignal("kyc-triggered")
Layer 5:  [verify-kyc-approved]                         ← @WaitForSignal("completion-requested")
Layer 6:  [activate-party]
Layer 7:  [send-completion-notification]
```

### Business Onboarding

Base path: `/api/v1/onboarding/businesses`

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/v1/onboarding/businesses` | Initiate business onboarding — registers the business party, opens a KYB case, sends a welcome notification; returns `onboardingId` | `201 Created` |
| `GET` | `/api/v1/onboarding/businesses/{onboardingId}` | Get journey status — returns `currentPhase`, `completedSteps`, `nextStep`, and `kybStatus` | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/company-data` | Submit company data — delivers `company-data-submitted` signal with legal name, tax ID, address, and business activity | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/ubos` | Submit UBOs — delivers `ubos-submitted` signal with UBO declarations (ownership percentages, PEP status); attaches each as KYB evidence | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/corporate-documents` | Submit corporate documents — delivers `corporate-documents-submitted` signal; attaches articles of incorporation, board resolution, and related documents as KYB evidence | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/authorized-signers` | Submit authorized signers — delivers `authorized-signers-submitted` signal; attaches legal representatives and power of attorney holders as KYB evidence | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/kyb` | Trigger KYB verification — delivers `kyb-triggered` signal; starts the Know Your Business verification process | `202 Accepted` |
| `GET` | `/api/v1/onboarding/businesses/{onboardingId}/kyb/status` | Get KYB status — retrieves the current verification status from the KYB case | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/completion` | Complete business onboarding — delivers `completion-requested` signal; verifies KYB approval, activates the business party, sends a completion notification | `200 OK` |

**Business workflow signal gates:**

```
Layer 0:  [register-business-party]                         ← compensatable
Layer 1:  [open-kyb-case] [send-welcome-notification]       ← parallel; KYB case compensatable
Layer 2:  [receive-company-data]                            ← @WaitForSignal("company-data-submitted")
Layer 3:  [receive-ubos]                                    ← @WaitForSignal("ubos-submitted")
Layer 4:  [receive-corporate-documents]                     ← @WaitForSignal("corporate-documents-submitted")
Layer 5:  [receive-authorized-signers]                      ← @WaitForSignal("authorized-signers-submitted")
Layer 6:  [trigger-kyb-verification]                        ← @WaitForSignal("kyb-triggered")
Layer 7:  [verify-kyb-approved]                             ← @WaitForSignal("completion-requested")
Layer 8:  [activate-business-party]
Layer 9:  [send-completion-notification]
```

## Domain SDK Dependencies

| SDK | ClientFactory | APIs Used | Purpose |
|-----|--------------|-----------|---------|
| `domain-customer-people-sdk` | `CustomerPeopleClientFactory` | `CustomersApi`, `BusinessesApi` | Register individual and business parties, add addresses, activate and deactivate parties |
| `domain-customer-kyc-kyb-sdk` | `CustomerKycKybClientFactory` | `KycApi`, `KybApi` | Open KYC/KYB cases, attach evidence documents, trigger verification, query case status |
| `domain-common-notifications-sdk` | `CommonNotificationsClientFactory` | `NotificationsApi` | Send welcome notifications at journey start and completion notifications at activation |

## Configuration

```yaml
server:
  port: ${SERVER_PORT:8096}

spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}

firefly:
  orchestration:
    persistence:
      provider: redis
      key-prefix: "exp-onboarding:"
      key-ttl: 72h
      retention-period: 30d
      cleanup-interval: 6h
    recovery:
      enabled: true
      stale-threshold: 1h
  cqrs:
    enabled: true
    command.timeout: 30s
    query:
      timeout: 15s
      caching-enabled: true
      cache-ttl: 5m

api-configuration:
  domain-platform:
    customer-people:
      base-path: ${CUSTOMER_PEOPLE_URL:http://localhost:8081}
    customer-kyc-kyb:
      base-path: ${CUSTOMER_KYC_KYB_URL:http://localhost:8083}
    common-notifications:
      base-path: ${COMMON_NOTIFICATIONS_URL:http://localhost:8095}
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8096` | HTTP server port |
| `REDIS_HOST` | `localhost` | Redis host for workflow state persistence |
| `REDIS_PORT` | `6379` | Redis port |
| `CUSTOMER_PEOPLE_URL` | `http://localhost:8081` | Base URL for `domain-customer-people` |
| `CUSTOMER_KYC_KYB_URL` | `http://localhost:8083` | Base URL for `domain-customer-kyc-kyb` |
| `COMMON_NOTIFICATIONS_URL` | `http://localhost:8095` | Base URL for `domain-common-notifications` |

## Running Locally

```bash
# Prerequisites — ensure domain-customer-people, domain-customer-kyc-kyb,
# domain-common-notifications, and Redis are running
cd exp-onboarding
mvn spring-boot:run -pl exp-onboarding-web
```

Server starts on port `8096`. Swagger UI: [http://localhost:8096/swagger-ui.html](http://localhost:8096/swagger-ui.html)

Swagger UI is disabled in the `pro` profile.

## Testing

```bash
mvn clean verify
```

Tests cover `IndividualOnboardingServiceImpl` and `BusinessOnboardingServiceImpl` (unit tests with mocked `WorkflowEngine`, `SignalService`, and `WorkflowQueryService`), and `IndividualOnboardingController` and `BusinessOnboardingController` (WebTestClient-based tests verifying HTTP status codes and response shapes for all 16 endpoints).
