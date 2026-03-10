# exp-onboarding

Backend-for-Frontend (BFF) experience-layer service that orchestrates customer onboarding journeys for both individuals and businesses. It composes calls to downstream domain services -- customer-people, KYC/KYB, and notifications -- through signal-driven workflows, exposing a step-by-step REST API that front-end applications consume to guide users through the full onboarding lifecycle.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Functional Verticals](#functional-verticals)
- [API Endpoints](#api-endpoints)
- [Workflow Details](#workflow-details)
- [Domain SDK Dependencies](#domain-sdk-dependencies)
- [Setup](#setup)
- [Testing](#testing)

---

## Overview

`exp-onboarding` is an `exp-*` (experience-layer) service in the Firefly platform. Unlike domain services that own data and enforce business rules, experience-layer services act as orchestration facades: they compose multiple domain calls into cohesive user journeys and present a simplified API tailored to front-end needs.

This service uses the **FireflyFramework Orchestration** engine with **signal-driven workflows** (`@Workflow` + `@WaitForSignal`). Each onboarding journey is modelled as a long-running workflow whose steps execute automatically until they reach a signal gate. The front-end advances the journey by calling successive REST endpoints, each of which delivers a signal to the workflow engine. This pattern provides:

- **Durable execution** -- workflow state is persisted to Redis, surviving process restarts.
- **Step-level observability** -- each step's status (PENDING, RUNNING, DONE, FAILED) is queryable at any time.
- **Automatic compensation** -- if a later step fails, compensatable steps roll back via their declared compensation methods.
- **Timeout enforcement** -- workflows expire after a configurable deadline (24 h for individuals, 48 h for businesses).

---

## Architecture

### Module Structure

| Module | Purpose |
|--------|---------|
| `exp-onboarding-core` | Business logic: workflow definitions, service interfaces/implementations, commands, and query DTOs. |
| `exp-onboarding-interfaces` | Interface adapters: bridges between web layer and core domain; depends on core. |
| `exp-onboarding-infra` | Infrastructure: SDK client factories, configuration properties, external client setup. |
| `exp-onboarding-web` | Deployable Spring Boot application: REST controllers, OpenAPI config, actuator endpoints. |
| `exp-onboarding-sdk` | Client SDK placeholder for consumers of this service's API. |

### Dependency Flow

```
web --> interfaces --> core --> infra
```

### Architecture Pattern: Signal-Driven Workflows

Each onboarding vertical is implemented as a `@Workflow` class. The service layer starts a workflow (SYNC mode) and then delivers signals from subsequent REST calls. The workflow engine resumes execution at each `@WaitForSignal` gate, runs the corresponding step against downstream domain SDKs, and pauses again at the next gate.

```
┌─────────────────────────────────────────────────────────────┐
│                     exp-onboarding (BFF)                    │
│                                                             │
│  Controller ──▶ Service ──▶ WorkflowEngine / SignalService  │
│                                     │                       │
│                              ┌──────┼──────┐                │
│                              ▼      ▼      ▼                │
│                         Workflow  Workflow  ...              │
│                         Steps    Steps                      │
└──────────────────────────┬──────┬──────┬────────────────────┘
                           │      │      │
                           ▼      ▼      ▼
                ┌──────────┐ ┌────────┐ ┌──────────────┐
                │ customer │ │  kyc/  │ │ notifications│
                │  people  │ │  kyb   │ │              │
                │  (SDK)   │ │ (SDK)  │ │    (SDK)     │
                └──────────┘ └────────┘ └──────────────┘
```

### Technology Stack

| Technology | Purpose |
|------------|---------|
| Java 25 | Language runtime |
| Spring Boot (WebFlux) | Reactive web framework |
| Project Reactor | Reactive streams |
| FireflyFramework Orchestration | Signal-driven workflow engine (`@Workflow`, `@WaitForSignal`, `@WorkflowStep`) |
| FireflyFramework Utils | Common utilities |
| FireflyFramework Validators | Validation utilities |
| FireflyFramework Web | Common web configurations and error handling |
| Redis | Workflow state persistence |
| domain-customer-people-sdk | SDK client for the Customer People domain service |
| domain-customer-kyc-kyb-sdk | SDK client for the KYC/KYB domain service |
| domain-common-notifications-sdk | SDK client for the Notifications domain service |
| SpringDoc OpenAPI (WebFlux UI) | API documentation |
| Micrometer + Prometheus | Metrics and monitoring |
| MapStruct | Object mapping |
| Lombok | Boilerplate reduction |

---

## Functional Verticals

### Individual Onboarding (7 endpoints)

Guides a natural person through registration, personal-data collection, identity-document upload, KYC verification, and party activation.

### Business Onboarding (9 endpoints)

Guides a legal entity through registration, company-data collection, UBO declarations, corporate-document upload, authorized-signer submission, KYB verification, and party activation.

---

## API Endpoints

All endpoints return reactive `Mono<ResponseEntity<...>>` responses. The base API path prefix is `/api/v1/onboarding`.

### Individual Onboarding (`/api/v1/onboarding/individuals`)

| Method | Path | Summary | Response Code |
|--------|------|---------|---------------|
| `POST` | `/api/v1/onboarding/individuals` | Initiate Onboarding -- registers the party, opens a KYC case, sends a welcome notification | `201 Created` |
| `GET` | `/api/v1/onboarding/individuals/{onboardingId}` | Get Onboarding Status -- returns completed steps, current phase, and next expected action | `200 OK` |
| `POST` | `/api/v1/onboarding/individuals/{onboardingId}/personal-data` | Submit Personal Data -- submits address and personal details; advances past the personal-data gate | `200 OK` |
| `POST` | `/api/v1/onboarding/individuals/{onboardingId}/identity-documents` | Submit Identity Documents -- uploads identity documents for KYC evidence; advances past the identity-documents gate | `200 OK` |
| `POST` | `/api/v1/onboarding/individuals/{onboardingId}/kyc` | Trigger KYC Verification -- triggers KYC verification after documents are submitted | `202 Accepted` |
| `GET` | `/api/v1/onboarding/individuals/{onboardingId}/kyc/status` | Get KYC Status -- retrieves the KYC verification status | `200 OK` |
| `POST` | `/api/v1/onboarding/individuals/{onboardingId}/completion` | Complete Onboarding -- verifies KYC approval, activates the party, sends a completion notification | `200 OK` |

### Business Onboarding (`/api/v1/onboarding/businesses`)

| Method | Path | Summary | Response Code |
|--------|------|---------|---------------|
| `POST` | `/api/v1/onboarding/businesses` | Initiate Business Onboarding -- registers the business party, opens a KYB case, sends a welcome notification | `201 Created` |
| `GET` | `/api/v1/onboarding/businesses/{onboardingId}` | Get Onboarding Status -- returns completed steps, current phase, and next expected action | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/company-data` | Submit Company Data -- submits legal name, tax ID, address, and business activity | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/ubos` | Submit Ultimate Beneficial Owners -- submits UBO declarations with ownership percentages and PEP status | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/corporate-documents` | Submit Corporate Documents -- submits articles of incorporation, board resolution, proof of address, tax certificate | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/authorized-signers` | Submit Authorized Signers -- submits legal representatives and power of attorney holders | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/kyb` | Trigger KYB Verification -- triggers Know Your Business verification after all data is submitted | `202 Accepted` |
| `GET` | `/api/v1/onboarding/businesses/{onboardingId}/kyb/status` | Get KYB Status -- retrieves the KYB verification status | `200 OK` |
| `POST` | `/api/v1/onboarding/businesses/{onboardingId}/completion` | Complete Onboarding -- verifies KYB approval, activates the business party, sends a completion notification | `200 OK` |

---

## Workflow Details

### Individual Onboarding Workflow

**Workflow ID:** `individual-onboarding`
**Timeout:** 24 hours (86 400 000 ms)
**Trigger Mode:** SYNC (blocks until the first signal gate)

#### Step Execution Flow

```
Layer 0:  [register-party]                             ← compensatable (closure on failure)
Layer 1:  [open-kyc-case] [send-welcome]               ← parallel; KYC case compensatable (fail on failure)
Layer 2:  [receive-personal-data]                       ← @WaitForSignal("personal-data-submitted")
Layer 3:  [receive-identity-docs]                       ← @WaitForSignal("identity-docs-submitted")
Layer 4:  [trigger-kyc-verification]                    ← @WaitForSignal("kyc-triggered")
Layer 5:  [verify-kyc-approved]                         ← @WaitForSignal("completion-requested")
Layer 6:  [activate-party]
Layer 7:  [send-completion-notification]
```

#### Signals

| Signal Name | Delivered By | Purpose |
|-------------|-------------|---------|
| `personal-data-submitted` | `POST .../personal-data` | Carries address and personal details; registers address via Customer People SDK |
| `identity-docs-submitted` | `POST .../identity-documents` | Carries document metadata; attaches evidence to the KYC case |
| `kyc-triggered` | `POST .../kyc` | Parameterless; triggers KYC verification via KYC/KYB SDK |
| `completion-requested` | `POST .../completion` | Parameterless; verifies KYC is APPROVED, then activates the party |

#### Compensation

| Step | Compensation Method | Action |
|------|-------------------|--------|
| `register-party` | `compensateDeactivateParty` | Requests customer closure via Customer People SDK |
| `open-kyc-case` | `compensateCancelKycCase` | Fails the KYC case with reason "Onboarding cancelled" |

#### Workflow Query

The `journeyStatus` query reconstructs a `JourneyStatusDTO` from persisted step statuses, exposing: `onboardingId`, `partyId`, `kycCaseId`, `currentPhase`, `completedSteps`, `nextStep`, and `kycVerificationStatus`.

---

### Business Onboarding Workflow

**Workflow ID:** `business-onboarding`
**Timeout:** 48 hours (172 800 000 ms)
**Trigger Mode:** SYNC

#### Step Execution Flow

```
Layer 0:  [register-business-party]                         ← compensatable (closure on failure)
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

#### Signals

| Signal Name | Delivered By | Purpose |
|-------------|-------------|---------|
| `company-data-submitted` | `POST .../company-data` | Carries legal name, trade name, tax ID, address; updates entity and registers address |
| `ubos-submitted` | `POST .../ubos` | Carries UBO list; attaches each UBO as KYB evidence |
| `corporate-documents-submitted` | `POST .../corporate-documents` | Carries document list; attaches each document as KYB evidence |
| `authorized-signers-submitted` | `POST .../authorized-signers` | Carries signer list; attaches each signer as KYB evidence |
| `kyb-triggered` | `POST .../kyb` | Parameterless; triggers KYB verification via KYC/KYB SDK |
| `completion-requested` | `POST .../completion` | Parameterless; verifies KYB is APPROVED/VERIFIED, then activates the party |

#### Compensation

| Step | Compensation Method | Action |
|------|-------------------|--------|
| `register-business-party` | `compensateDeactivateParty` | Requests business closure via Customer People SDK |
| `open-kyb-case` | `compensateCancelKybCase` | Fails the KYB case with reason "Business onboarding cancelled" |

#### Workflow Query

The `journeyStatus` query reconstructs a `BusinessOnboardingStatusDTO` from persisted step statuses, exposing: `onboardingId`, `partyId`, `kybCaseId`, `currentPhase`, `completedSteps`, `nextStep`, and `kybStatus`.

---

## Domain SDK Dependencies

| SDK | Client Factory | APIs Exposed | Purpose |
|-----|---------------|-------------|---------|
| `domain-customer-people-sdk` | `CustomerPeopleClientFactory` | `CustomersApi`, `BusinessesApi` | Register individual/business parties, add addresses, activate/deactivate parties |
| `domain-customer-kyc-kyb-sdk` | `CustomerKycKybClientFactory` | `KycApi`, `KybApi` | Open KYC/KYB cases, attach evidence, trigger verification, query case status |
| `domain-common-notifications-sdk` | `CommonNotificationsClientFactory` | `NotificationsApi` | Send welcome and completion notifications |

Each factory reads its base path from `application.yaml` via a corresponding `@ConfigurationProperties` class:

| Properties Class | Config Prefix | Default Base Path |
|-----------------|--------------|-------------------|
| `CustomerPeopleProperties` | `api-configuration.domain-platform.customer-people` | `http://localhost:8081` |
| `CustomerKycKybProperties` | `api-configuration.domain-platform.customer-kyc-kyb` | `http://localhost:8083` |
| `CommonNotificationsProperties` | `api-configuration.domain-platform.common-notifications` | `http://localhost:8095` |

---

## Setup

### Prerequisites

- **Java 25** (or later)
- **Apache Maven 3.9+**
- **Redis** (used for workflow state persistence)
- Access to the FireflyFramework Maven repository for `org.fireflyframework` dependencies
- Access to the domain SDK artifacts (`domain-customer-people-sdk`, `domain-customer-kyc-kyb-sdk`, `domain-common-notifications-sdk`)

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8096` | HTTP port the server binds to |
| `REDIS_HOST` | `localhost` | Redis host for workflow state persistence |
| `REDIS_PORT` | `6379` | Redis port |

### Configuration

Key configuration sections in `application.yaml`:

| Section | Description |
|---------|-------------|
| `firefly.orchestration.persistence` | Redis-backed workflow state persistence with 72 h TTL and 30 d retention |
| `firefly.orchestration.recovery` | Automatic recovery of stale workflows (threshold: 1 h) |
| `firefly.cqrs` | CQRS command/query configuration with timeouts, metrics, and query caching (5 min TTL) |
| `firefly.stepevents` | Publishes step-level events for observability |
| `api-configuration.domain-platform.*` | Base paths for downstream domain services |

### Build

```bash
# Full build
mvn clean install

# Skip tests
mvn clean install -DskipTests
```

### Run

```bash
# Run via Spring Boot Maven plugin
mvn -pl exp-onboarding-web spring-boot:run

# Or run the packaged JAR
java -jar exp-onboarding-web/target/exp-onboarding.jar
```

Once running, access the Swagger UI at `http://localhost:8096/webjars/swagger-ui/index.html` (disabled in the `pro` profile).

---

## Testing

The project includes unit tests for both service implementations and controllers:

| Test Class | Module | Scope |
|-----------|--------|-------|
| `IndividualOnboardingServiceImplTest` | `exp-onboarding-core` | Tests signal delivery and workflow engine interactions for the individual journey |
| `BusinessOnboardingServiceImplTest` | `exp-onboarding-core` | Tests signal delivery and workflow engine interactions for the business journey |
| `IndividualOnboardingControllerTest` | `exp-onboarding-web` | Tests REST endpoint routing and response codes for individual onboarding |
| `BusinessOnboardingControllerTest` | `exp-onboarding-web` | Tests REST endpoint routing and response codes for business onboarding |

Run all tests:

```bash
mvn test
```

Run tests for a specific module:

```bash
mvn -pl exp-onboarding-core test
mvn -pl exp-onboarding-web test
```
