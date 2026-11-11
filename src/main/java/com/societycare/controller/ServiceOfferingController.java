package com.societycare.controller;

import com.societycare.config.OpenApiConfig;
import com.societycare.dto.request.ServiceOfferingRequest;
import com.societycare.dto.response.PageResponse;
import com.societycare.dto.response.ServiceOfferingResponse;
import com.societycare.service.ServiceOfferingService;
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

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Tag(name = "Service Offerings")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ServiceOfferingController {

    private final ServiceOfferingService serviceOfferingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "Create a service offering (SERVICE_PROVIDER for themselves, ADMIN for any provider)")
    public ResponseEntity<ServiceOfferingResponse> create(@Valid @RequestBody ServiceOfferingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOfferingService.create(request));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List services (citizen: active services of own society; provider: own; admin: all)")
    public ResponseEntity<PageResponse<ServiceOfferingResponse>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(serviceOfferingService.getAll(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a service offering")
    public ResponseEntity<ServiceOfferingResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceOfferingService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "Update a service offering (ADMIN, or the owning SERVICE_PROVIDER)")
    public ResponseEntity<ServiceOfferingResponse> update(@PathVariable Long id,
                                                          @Valid @RequestBody ServiceOfferingRequest request) {
        return ResponseEntity.ok(serviceOfferingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "Delete a service offering that no campaign uses")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        serviceOfferingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
