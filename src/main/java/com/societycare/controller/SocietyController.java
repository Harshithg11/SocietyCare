package com.societycare.controller;

import com.societycare.config.OpenApiConfig;
import com.societycare.dto.request.SocietyRequest;
import com.societycare.dto.response.PageResponse;
import com.societycare.dto.response.SocietyResponse;
import com.societycare.service.SocietyService;
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
@RequestMapping("/api/societies")
@RequiredArgsConstructor
@Tag(name = "Societies")
public class SocietyController {

    private final SocietyService societyService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "ADMIN: create a society")
    public ResponseEntity<SocietyResponse> create(@Valid @RequestBody SocietyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(societyService.create(request));
    }

    /** Public (no token) so people can choose their society while registering. */
    @GetMapping
    @Operation(summary = "List societies (public, read-only, used by the registration form)")
    public ResponseEntity<PageResponse<SocietyResponse>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(societyService.getAll(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a society by id (public, read-only)")
    public ResponseEntity<SocietyResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(societyService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "ADMIN: update a society")
    public ResponseEntity<SocietyResponse> update(@PathVariable Long id, @Valid @RequestBody SocietyRequest request) {
        return ResponseEntity.ok(societyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "ADMIN: delete a society that has no users, services or campaigns")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        societyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
