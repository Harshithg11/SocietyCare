package com.societycare.service;

import com.societycare.dto.response.BookingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingService {

    BookingResponse joinCampaign(Long campaignId);

    BookingResponse cancelBooking(Long bookingId);

    Page<BookingResponse> getMyBookings(Pageable pageable);

    Page<BookingResponse> getAllBookings(Pageable pageable);

    BookingResponse getBooking(Long id);

    Page<BookingResponse> getCampaignBookings(Long campaignId, Pageable pageable);
}
