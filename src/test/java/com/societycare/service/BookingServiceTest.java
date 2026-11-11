package com.societycare.service;

import com.societycare.dto.response.BookingResponse;
import com.societycare.entity.Booking;
import com.societycare.entity.BookingCampaign;
import com.societycare.entity.ServiceOffering;
import com.societycare.entity.Society;
import com.societycare.entity.User;
import com.societycare.enums.BookingStatus;
import com.societycare.enums.CampaignStatus;
import com.societycare.enums.Role;
import com.societycare.exception.BusinessRuleException;
import com.societycare.exception.CampaignExpiredException;
import com.societycare.exception.DuplicateResourceException;
import com.societycare.exception.InvalidCampaignStateException;
import com.societycare.exception.UnauthorizedOperationException;
import com.societycare.repository.BookingCampaignRepository;
import com.societycare.repository.BookingRepository;
import com.societycare.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingCampaignRepository campaignRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private Society society;
    private User citizen;

    @BeforeEach
    void setUp() {
        society = society(1L);
        citizen = user(10L, Role.CITIZEN, society);

        when(currentUserService.getCurrentUser()).thenReturn(citizen);
        when(bookingRepository.findByCitizenIdAndBookingCampaignId(any(), any())).thenReturn(Optional.empty());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(500L);
            }
            return b;
        });
        when(campaignRepository.save(any(BookingCampaign.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------- helpers ----------------

    private Society society(Long id) {
        Society s = new Society();
        s.setId(id);
        s.setSocietyName("Society " + id);
        return s;
    }

    private User user(Long id, Role role, Society s) {
        User u = new User();
        u.setId(id);
        u.setFullName("User " + id);
        u.setEmail("user" + id + "@example.com");
        u.setPhoneNumber("9000000000");
        u.setRole(role);
        u.setSociety(s);
        return u;
    }

    /** An open campaign: started yesterday, ends in 10 days. */
    private BookingCampaign campaign(int current, int min, Integer max, CampaignStatus status) {
        User provider = user(99L, Role.SERVICE_PROVIDER, society);
        ServiceOffering offering = new ServiceOffering();
        offering.setId(7L);
        offering.setServiceName("AC Servicing");
        offering.setServiceProvider(provider);
        offering.setSociety(society);

        BookingCampaign c = new BookingCampaign();
        c.setId(1L);
        c.setCampaignTitle("AC Offer");
        c.setServiceOffering(offering);
        c.setSociety(society);
        c.setMinimumParticipants(min);
        c.setMaximumParticipants(max);
        c.setCurrentParticipants(current);
        c.setRegularPrice(new BigDecimal("1000.00"));
        c.setDiscountPercentage(new BigDecimal("20.00"));
        c.setDiscountedPrice(new BigDecimal("800.00"));
        c.setStartDate(LocalDate.now().minusDays(1));
        c.setEndDate(LocalDate.now().plusDays(10));
        c.setStatus(status);
        when(campaignRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(c));
        return c;
    }

    private Booking booking(Long id, User owner, BookingCampaign c, BookingStatus status) {
        Booking b = new Booking();
        b.setId(id);
        b.setCitizen(owner);
        b.setBookingCampaign(c);
        b.setBookingStatus(status);
        b.setBookedAt(LocalDateTime.now().minusHours(1));
        when(bookingRepository.findCampaignIdById(id)).thenReturn(Optional.of(c.getId()));
        when(bookingRepository.findById(id)).thenReturn(Optional.of(b));
        return b;
    }

    // ---------------- joining ----------------

    @Test
    void citizenJoinsCampaignAndCountIncrements() {
        BookingCampaign c = campaign(5, 10, null, CampaignStatus.PENDING);

        BookingResponse response = bookingService.joinCampaign(1L);

        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.BOOKED);
        assertThat(response.currentParticipants()).isEqualTo(6);
        assertThat(c.getCurrentParticipants()).isEqualTo(6);
        assertThat(response.minimumParticipants()).isEqualTo(10);
        assertThat(response.message()).contains("successfully joined");
        verify(campaignRepository).save(c);
    }

    @Test
    void campaignStaysPendingBelowTheMinimum() {
        BookingCampaign c = campaign(8, 10, null, CampaignStatus.PENDING);

        BookingResponse response = bookingService.joinCampaign(1L);

        assertThat(response.campaignStatus()).isEqualTo(CampaignStatus.PENDING);
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.PENDING);
    }

    @Test
    void campaignBecomesConfirmedWhenMinimumIsReached() {
        BookingCampaign c = campaign(9, 10, null, CampaignStatus.PENDING);

        BookingResponse response = bookingService.joinCampaign(1L);

        assertThat(response.currentParticipants()).isEqualTo(10);
        assertThat(response.campaignStatus()).isEqualTo(CampaignStatus.CONFIRMED);
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.CONFIRMED);
        assertThat(response.message()).contains("confirmed");
    }

    @Test
    void duplicateActiveBookingIsRejected() {
        BookingCampaign c = campaign(5, 10, null, CampaignStatus.PENDING);
        Booking existing = new Booking();
        existing.setId(77L);
        existing.setCitizen(citizen);
        existing.setBookingCampaign(c);
        existing.setBookingStatus(BookingStatus.BOOKED);
        when(bookingRepository.findByCitizenIdAndBookingCampaignId(10L, 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> bookingService.joinCampaign(1L))
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(c.getCurrentParticipants()).isEqualTo(5);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void cancelledBookingIsReactivatedWhenCitizenJoinsAgain() {
        BookingCampaign c = campaign(3, 10, null, CampaignStatus.PENDING);
        Booking cancelled = new Booking();
        cancelled.setId(77L);
        cancelled.setCitizen(citizen);
        cancelled.setBookingCampaign(c);
        cancelled.setBookingStatus(BookingStatus.CANCELLED);
        cancelled.setCancelledAt(LocalDateTime.now().minusDays(1));
        when(bookingRepository.findByCitizenIdAndBookingCampaignId(10L, 1L)).thenReturn(Optional.of(cancelled));

        BookingResponse response = bookingService.joinCampaign(1L);

        assertThat(response.bookingId()).isEqualTo(77L);
        assertThat(cancelled.getBookingStatus()).isEqualTo(BookingStatus.BOOKED);
        assertThat(cancelled.getCancelledAt()).isNull();
        assertThat(c.getCurrentParticipants()).isEqualTo(4);
    }

    @Test
    void citizenFromAnotherSocietyIsRejected() {
        campaign(5, 10, null, CampaignStatus.PENDING);
        when(currentUserService.getCurrentUser()).thenReturn(user(11L, Role.CITIZEN, society(2L)));

        assertThatThrownBy(() -> bookingService.joinCampaign(1L))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void onlyCitizensCanJoin() {
        campaign(5, 10, null, CampaignStatus.PENDING);
        when(currentUserService.getCurrentUser()).thenReturn(user(12L, Role.SERVICE_PROVIDER, society));

        assertThatThrownBy(() -> bookingService.joinCampaign(1L))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void expiredCampaignCannotBeJoined() {
        campaign(5, 10, null, CampaignStatus.EXPIRED);

        assertThatThrownBy(() -> bookingService.joinCampaign(1L))
                .isInstanceOf(InvalidCampaignStateException.class);
    }

    @Test
    void completedCampaignCannotBeJoined() {
        campaign(10, 10, null, CampaignStatus.COMPLETED);

        assertThatThrownBy(() -> bookingService.joinCampaign(1L))
                .isInstanceOf(InvalidCampaignStateException.class);
    }

    @Test
    void campaignThatHasNotStartedCannotBeJoined() {
        BookingCampaign c = campaign(0, 10, null, CampaignStatus.PENDING);
        c.setStartDate(LocalDate.now().plusDays(2));

        assertThatThrownBy(() -> bookingService.joinCampaign(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not started");
    }

    @Test
    void pendingCampaignPastItsEndDateIsExpiredAndJoinIsRejected() {
        BookingCampaign c = campaign(5, 10, null, CampaignStatus.PENDING);
        c.setEndDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> bookingService.joinCampaign(1L))
                .isInstanceOf(CampaignExpiredException.class);
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.EXPIRED);
        verify(campaignRepository).save(c);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void confirmedCampaignWithoutMaximumIsClosed() {
        campaign(10, 10, null, CampaignStatus.CONFIRMED);

        assertThatThrownBy(() -> bookingService.joinCampaign(1L))
                .isInstanceOf(InvalidCampaignStateException.class);
    }

    @Test
    void confirmedCampaignKeepsAcceptingJoinsUntilMaximumIsReached() {
        BookingCampaign c = campaign(10, 10, 12, CampaignStatus.CONFIRMED);

        BookingResponse response = bookingService.joinCampaign(1L);

        assertThat(response.currentParticipants()).isEqualTo(11);
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.CONFIRMED);
    }

    @Test
    void maximumParticipantLimitIsEnforced() {
        BookingCampaign c = campaign(12, 10, 12, CampaignStatus.CONFIRMED);

        assertThatThrownBy(() -> bookingService.joinCampaign(1L))
                .isInstanceOf(InvalidCampaignStateException.class)
                .hasMessageContaining("maximum");
        assertThat(c.getCurrentParticipants()).isEqualTo(12);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    // ---------------- cancelling ----------------

    @Test
    void citizenCancelsOwnBookingAndCountDecrements() {
        BookingCampaign c = campaign(3, 10, null, CampaignStatus.PENDING);
        Booking b = booking(500L, citizen, c, BookingStatus.BOOKED);

        BookingResponse response = bookingService.cancelBooking(500L);

        assertThat(b.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.getCancelledAt()).isNotNull();
        assertThat(c.getCurrentParticipants()).isEqualTo(2);
        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.currentParticipants()).isEqualTo(2);
    }

    @Test
    void cancellingTwiceIsRejectedAndCountIsUnchanged() {
        BookingCampaign c = campaign(2, 10, null, CampaignStatus.PENDING);
        booking(500L, citizen, c, BookingStatus.CANCELLED);

        assertThatThrownBy(() -> bookingService.cancelBooking(500L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already cancelled");
        assertThat(c.getCurrentParticipants()).isEqualTo(2);
    }

    @Test
    void citizenCannotCancelAnotherUsersBooking() {
        BookingCampaign c = campaign(3, 10, null, CampaignStatus.PENDING);
        booking(500L, user(55L, Role.CITIZEN, society), c, BookingStatus.BOOKED);

        assertThatThrownBy(() -> bookingService.cancelBooking(500L))
                .isInstanceOf(UnauthorizedOperationException.class);
        assertThat(c.getCurrentParticipants()).isEqualTo(3);
    }

    @Test
    void citizenCannotCancelOnceCampaignIsConfirmed() {
        BookingCampaign c = campaign(10, 10, null, CampaignStatus.CONFIRMED);
        booking(500L, citizen, c, BookingStatus.BOOKED);

        assertThatThrownBy(() -> bookingService.cancelBooking(500L))
                .isInstanceOf(InvalidCampaignStateException.class);
        assertThat(c.getCurrentParticipants()).isEqualTo(10);
    }

    @Test
    void adminCanCancelOnConfirmedCampaignAndCampaignStaysConfirmed() {
        BookingCampaign c = campaign(10, 10, null, CampaignStatus.CONFIRMED);
        Booking b = booking(500L, citizen, c, BookingStatus.BOOKED);
        when(currentUserService.getCurrentUser()).thenReturn(user(1L, Role.ADMIN, null));

        bookingService.cancelBooking(500L);

        assertThat(b.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(c.getCurrentParticipants()).isEqualTo(9);
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.CONFIRMED);
    }

    @Test
    void nobodyCanCancelAfterCompletion() {
        BookingCampaign c = campaign(10, 10, null, CampaignStatus.COMPLETED);
        booking(500L, citizen, c, BookingStatus.BOOKED);
        when(currentUserService.getCurrentUser()).thenReturn(user(1L, Role.ADMIN, null));

        assertThatThrownBy(() -> bookingService.cancelBooking(500L))
                .isInstanceOf(InvalidCampaignStateException.class);
    }

    @Test
    void participantCountNeverGoesNegative() {
        BookingCampaign c = campaign(0, 10, null, CampaignStatus.PENDING);
        booking(500L, citizen, c, BookingStatus.BOOKED);

        assertThatThrownBy(() -> bookingService.cancelBooking(500L))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(c.getCurrentParticipants()).isZero();
    }
}
