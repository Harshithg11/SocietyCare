package com.societycare.controller;

import com.societycare.config.OpenApiConfig;
import com.societycare.dto.response.BookingResponse;
import com.societycare.dto.response.PageResponse;
import com.societycare.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/campaigns/{campaignId}/join")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "Join a campaign. The campaign is confirmed automatically when the minimum is reached.")
    public ResponseEntity<BookingResponse> join(@PathVariable Long campaignId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.joinCampaign(campaignId));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CITIZEN','ADMIN')")
    @Operation(summary = "Cancel a booking (the owner, or an ADMIN). Returns the cancelled booking.")
    public ResponseEntity<BookingResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @GetMapping("/my-bookings")
    @PreAuthorize("hasRole('CITIZEN')")
    @Operation(summary = "My bookings")
    public ResponseEntity<PageResponse<BookingResponse>> myBookings(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(bookingService.getMyBookings(pageable)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "ADMIN: all bookings. SERVICE_PROVIDER: bookings of their own services.")
    public ResponseEntity<PageResponse<BookingResponse>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(bookingService.getAllBookings(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a booking (its owner, an ADMIN, or the provider of the service)")
    public ResponseEntity<BookingResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }

    @GetMapping("/campaign/{campaignId}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE_PROVIDER')")
    @Operation(summary = "All bookings of a campaign (ADMIN, or the owning SERVICE_PROVIDER)")
    public ResponseEntity<PageResponse<BookingResponse>> getByCampaign(
            @PathVariable Long campaignId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(bookingService.getCampaignBookings(campaignId, pageable)));
    }
}
