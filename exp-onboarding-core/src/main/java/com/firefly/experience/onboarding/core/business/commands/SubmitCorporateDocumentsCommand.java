package com.firefly.experience.onboarding.core.business.commands;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Command DTO for submitting corporate documents during business onboarding.
 * Advances the journey past the corporate-documents-submitted signal gate.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmitCorporateDocumentsCommand {
    @Valid
    private List<CorporateDocumentEntry> documents;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CorporateDocumentEntry {
        private String documentType;
        private String documentReference;
    }
}
