package com.societycare.service.impl;

import com.societycare.dto.response.BookingResponse;
import com.societycare.entity.Booking;
import com.societycare.entity.BookingCampaign;
import com.societycare.entity.User;
import com.societycare.enums.BookingStatus;
import com.societycare.enums.CampaignStatus;
import com.societycare.enums.Role;
import com.societycare.exception.BusinessRuleException;
import com.societycare.exception.CampaignExpiredException;
import com.societycare.exception.DuplicateResourceException;
import com.societycare.exception.InvalidCampaignStateException;
import com.societycare.exception.ResourceNotFoundException;
import com.societycare.exception.UnauthorizedOperationException;
import com.societycare.repository.BookingCampaignRepository;
import com.societycare.repository.BookingRepository;
import com.societycare.service.BookingService;
import com.societycare.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CONCURRENCY MODEL
 * join and cancel both begin by loading the campaign with SELECT ... FOR UPDATE
 * (BookingCampaignRepository.findByIdForUpdate). All changes to a campaign's participant count and status are
 * therefore serialized by database row locking. That gives: no lost updates, no duplicate bookings, never more than
 * the maximum participants, and exactly one request that crosses the minimum. Everything runs in one transaction,
 * so a failure rolls back the booking and the counter together. The lock order is always campaign, then booking.
 * UNIQUE(citizen_id, campaign_id) in the database is the last line of defence against duplicates.
 *
 * JOIN POLICY (a deliberate reading of the spec): a campaign accepts joins while it is PENDING, and also while it is
 * CONFIRMED if a maximum was set and has not been reached yet (so the group can keep filling up to the maximum).
 * EXPIRED and COMPLETED campaigns never accept joins.
 *
 * CANCELLATION POLICY: only BOOKED bookings can be cancelled, by their owner or an ADMIN.
 * Citizens can cancel only while the campaign is PENDING (a CONFIRMED group price is committed).
 * An ADMIN may also cancel on a CONFIRMED campaign; the campaign then stays CONFIRMED.
 * Nobody can cancel on EXPIRED or COMPLETED campaigns.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    static final String MSG_JOINED = "You have successfully joined the campaign.";
    static final String MSG_CONFIRMED =
            "Congratulations! The campaign has reached its minimum participant requirement and is now confirmed.";
    static final String MSG_CANCELLED = "Your booking has been cancelled.";

    private final BookingRepository bookingRepository;
    private final BookingCampaignRepository campaignRepository;
    private final CurrentUserService currentUserService;

    /**
     * noRollbackFor: if a join attempt finds that the end date has passed we mark the campaign EXPIRED and then
     * reject the request. The EXPIRED status must still be committed, so this exception does not roll back.
     */
    @Override
    @Transactional(noRollbackFor = CampaignExpiredException.class)
    public BookingResponse joinCampaign(Long campaignId) {
        // 1-2. Authenticated user, must be a CITIZEN
        User citizen = currentUserService.getCurrentUser();
        if (citizen.getRole() != Role.CITIZEN) {
            throw new UnauthorizedOperationException("Only citizens can join campaigns.");
        }

        // Load AND lock the campaign: from here on we are the only writer of this campaign row.
        BookingCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id " + campaignId));

        // 3. Same society
        if (citizen.getSociety() == null || !citizen.getSociety().getId().equals(campaign.getSociety().getId())) {
            throw new UnauthorizedOperationException("You can only join campaigns of your own society.");
        }

        // 4. Status check (see JOIN POLICY above)
        CampaignStatus status = campaign.getStatus();
        if (status == CampaignStatus.EXPIRED || status == CampaignStatus.COMPLETED) {
            throw new InvalidCampaignStateException("The campaign is " + status + " and no longer accepting bookings.");
        }
        if (status == CampaignStatus.CONFIRMED && campaign.getMaximumParticipants() == null) {
            throw new InvalidCampaignStateException("The campaign is already confirmed and is no longer accepting bookings.");
        }

        // 5. Campaign must have started
        LocalDate today = LocalDate.now();
        if (campaign.getStartDate().isAfter(today)) {
            throw new BusinessRuleException("The campaign has not started yet. It opens on " + campaign.getStartDate() + ".");
        }

        // 6. Deadline: a PENDING campaign past its end date becomes EXPIRED (saved thanks to noRollbackFor)
        if (campaign.getEndDate().isBefore(today)) {
            if (status == CampaignStatus.PENDING) {
                campaign.setStatus(CampaignStatus.EXPIRED);
                campaignRepository.save(campaign);
                throw new CampaignExpiredException("The campaign deadline has passed and the campaign has expired.");
            }
            throw new InvalidCampaignStateException("The campaign deadline has passed.");
        }

        // 7. Duplicate check (a CANCELLED row is reactivated below)
        Booking booking = bookingRepository
                .findByCitizenIdAndBookingCampaignId(citizen.getId(), campaignId).orElse(null);
        if (booking != null && booking.getBookingStatus() == BookingStatus.BOOKED) {
            throw new DuplicateResourceException("You have already joined this campaign.");
        }

        // 8. Maximum participants
        Integer max = campaign.getMaximumParticipants();
        if (max != null && campaign.getCurrentParticipants() >= max) {
            throw new InvalidCampaignStateException("The campaign has reached its maximum number of participants.");
        }

        // 9. Create (or reactivate) the booking
        if (booking == null) {
            booking = new Booking();
            booking.setCitizen(citizen);
            booking.setBookingCampaign(campaign);
        }
        booking.setBookingStatus(BookingStatus.BOOKED);
        booking.setBookedAt(LocalDateTime.now());
        booking.setCancelledAt(null);
        booking = bookingRepository.save(booking);

        // 10. Increment participants (safe: we hold the row lock)
        campaign.setCurrentParticipants(campaign.getCurrentParticipants() + 1);

        // 11. Automatic confirmation when the minimum is reached
        boolean confirmedNow = false;
        if (campaign.getStatus() == CampaignStatus.PENDING
                && campaign.getCurrentParticipants() >= campaign.getMinimumParticipants()) {
            campaign.setStatus(CampaignStatus.CONFIRMED);
            confirmedNow = true;
            log.info("Campaign {} reached its minimum and is now CONFIRMED", campaignId);
        }

        // Booking and campaign changes are committed together at the end of this transaction.
        campaignRepository.save(campaign);
        return BookingResponse.from(booking, confirmedNow ? MSG_CONFIRMED : MSG_JOINED);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long bookingId) {
        User actor = currentUserService.getCurrentUser();

        // Scalar lookup (loads no entity) -> lock the campaign -> only THEN load the booking, so we read fresh data.
        Long campaignId = bookingRepository.findCampaignIdById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + bookingId));
        BookingCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id " + campaignId));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + bookingId));

        boolean isOwner = booking.getCitizen().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new UnauthorizedOperationException("You can only cancel your own bookings.");
        }
        if (booking.getBookingStatus() != BookingStatus.BOOKED) {
            throw new BusinessRuleException("This booking is already cancelled.");
        }

        CampaignStatus status = campaign.getStatus();
        if (status == CampaignStatus.EXPIRED || status == CampaignStatus.COMPLETED) {
            throw new InvalidCampaignStateException("Bookings of " + status + " campaigns cannot be cancelled.");
        }
        if (status == CampaignStatus.CONFIRMED && !isAdmin) {
            throw new InvalidCampaignStateException(
                    "This campaign is already confirmed. Please contact the administrator to cancel your booking.");
        }
        if (campaign.getCurrentParticipants() <= 0) {
            throw new BusinessRuleException("Participant count is already zero; the booking cannot be cancelled.");
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        campaign.setCurrentParticipants(campaign.getCurrentParticipants() - 1);
        bookingRepository.save(booking);
        campaignRepository.save(campaign);

        return BookingResponse.from(booking, MSG_CANCELLED);
    }

    @Override
    public Page<BookingResponse> getMyBookings(Pageable pageable) {
        User actor = currentUserService.getCurrentUser();
        return bookingRepository.findByCitizenId(actor.getId(), pageable).map(BookingResponse::from);
    }

    /** ADMIN: all bookings. SERVICE_PROVIDER: bookings for their own services. */
    @Override
    public Page<BookingResponse> getAllBookings(Pageable pageable) {
        User actor = currentUserService.getCurrentUser();
        Page<Booking> page = actor.getRole() == Role.ADMIN
                ? bookingRepository.findAll(pageable)
                : bookingRepository.findByBookingCampaignServiceOfferingServiceProviderId(actor.getId(), pageable);
        return page.map(BookingResponse::from);
    }

    @Override
    public BookingResponse getBooking(Long id) {
        User actor = currentUserService.getCurrentUser();
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id " + id));

        boolean allowed = switch (actor.getRole()) {
            case ADMIN -> true;
            case CITIZEN -> booking.getCitizen().getId().equals(actor.getId());
            case SERVICE_PROVIDER -> booking.getBookingCampaign().getServiceOffering().isProvidedBy(actor);
        };
        if (!allowed) {
            throw new UnauthorizedOperationException("You are not allowed to view this booking.");
        }
        return BookingResponse.from(booking);
    }

    @Override
    public Page<BookingResponse> getCampaignBookings(Long campaignId, Pageable pageable) {
        User actor = currentUserService.getCurrentUser();
        BookingCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id " + campaignId));

        boolean allowed = actor.getRole() == Role.ADMIN
                || (actor.getRole() == Role.SERVICE_PROVIDER && campaign.getServiceOffering().isProvidedBy(actor));
        if (!allowed) {
            throw new UnauthorizedOperationException("You are not allowed to view bookings of this campaign.");
        }
        return bookingRepository.findByBookingCampaignId(campaignId, pageable).map(BookingResponse::from);
    }
}
