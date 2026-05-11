package com.firefly.experience.onboarding.web.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.firefly.experience.onboarding.core.business.search.BusinessSearchRequest;
import com.firefly.experience.onboarding.core.business.search.BusinessSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * BFF endpoints for business search and detail lookup against the third-party
 * business intelligence provider (Moody's Orbis), exposed as a two-step flow:
 *
 * <ol>
 *   <li>{@code POST /search} returns the candidate matches list.</li>
 *   <li>{@code GET /{bvdId}} returns only the {@code companyData} subtree of
 *       the composite enrichment response for the chosen candidate.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/onboarding/businesses/search")
@RequiredArgsConstructor
@Tag(name = "Business Search",
        description = "Search and look up legal entities against the business intelligence provider (Orbis).")
public class BusinessSearchController {

    private final BusinessSearchService service;

    @Operation(summary = "Search businesses",
            description = "Returns the candidate list from the provider that match the supplied criteria. "
                    + "Either nationalId (CIF/VAT) or name must be supplied.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider match list",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = JsonNode.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "502", description = "Upstream provider error")
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<JsonNode>> search(@Valid @RequestBody BusinessSearchRequest request) {
        return service.searchMatches(request).map(ResponseEntity::ok);
    }

    @Operation(summary = "Get company data by BvD id",
            description = "Returns the companyData section of the composite enrichment response for the "
                    + "given BvD identifier (selected from a previous /search call).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company data subtree from provider",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = JsonNode.class))),
            @ApiResponse(responseCode = "400", description = "bvdId missing"),
            @ApiResponse(responseCode = "502", description = "Upstream provider error")
    })
    @GetMapping(path = "/{bvdId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<JsonNode>> getCompanyData(
            @Parameter(description = "BvD identifier returned by /search", example = "ES12345")
            @PathVariable String bvdId) {
        return service.getCompanyData(bvdId).map(ResponseEntity::ok);
    }
}
