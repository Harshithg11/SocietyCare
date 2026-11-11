package com.societycare.controller;

import com.societycare.config.OpenApiConfig;
import com.societycare.dto.request.CampaignRequest;
import com.societycare.dto.request.CampaignUpdateRequest;
import com.societycare.dto.response.CampaignResponse;
import com.societycare.dto.response.PageResponse;
import com.societycare.dto.response.ParticipantResponse;
import com.societycare.enums.CampaignStatus;
import com.societycare.service.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "Create a campaign (starts PENDING with 0 participants; discounted price is calculated)")
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.create(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List campaigns visible to me (citizen: own society; provider: own services; admin: all)")
    public ResponseEntity<PageResponse<CampaignResponse>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(campaignService.getAll(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a campaign")
    public ResponseEntity<CampaignResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getById(id));
    }

    @GetMapping("/society/{societyId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List campaigns of a society (citizens: only their own society)")
    public ResponseEntity<PageResponse<CampaignResponse>> getBySociety(
            @PathVariable Long societyId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(campaignService.getBySociety(societyId, pageable)));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List visible campaigns with a given status (PENDING, CONFIRMED, EXPIRED, COMPLETED)")
    public ResponseEntity<PageResponse<CampaignResponse>> getByStatus(
            @PathVariable CampaignStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(campaignService.getByStatus(status, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "Update a PENDING campaign (ADMIN, or the owning SERVICE_PROVIDER)")
    public ResponseEntity<CampaignResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody CampaignUpdateRequest request) {
        return ResponseEntity.ok(campaignService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "Delete a campaign that has no bookings")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "Mark a CONFIRMED campaign as COMPLETED (ADMIN, or the owning SERVICE_PROVIDER)")
    public ResponseEntity<CampaignResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.complete(id));
    }

    @GetMapping("/{id}/participants")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "Residents who have an active booking in the campaign (ADMIN, or the owning provider)")
    public ResponseEntity<PageResponse<ParticipantResponse>> getParticipants(
            @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(campaignService.getParticipants(id, pageable)));
    }

    @PostMapping("/expire-overdue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN: expire all PENDING campaigns whose end date has passed (same job as the scheduler)")
    public ResponseEntity<Map<String, Integer>> expireOverdue() {
        return ResponseEntity.ok(Map.of("expiredCampaigns", campaignService.expireOverdueCampaigns()));
    }
}
