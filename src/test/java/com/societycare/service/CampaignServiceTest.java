package com.societycare.service;

import com.societycare.dto.request.CampaignRequest;
import com.societycare.dto.response.CampaignResponse;
import com.societycare.entity.BookingCampaign;
import com.societycare.entity.ServiceOffering;
import com.societycare.entity.Society;
import com.societycare.entity.User;
import com.societycare.enums.CampaignStatus;
import com.societycare.enums.Role;
import com.societycare.exception.BusinessRuleException;
import com.societycare.exception.InvalidCampaignStateException;
import com.societycare.exception.ResourceNotFoundException;
import com.societycare.exception.UnauthorizedOperationException;
import com.societycare.repository.BookingCampaignRepository;
import com.societycare.repository.BookingRepository;
import com.societycare.repository.ServiceOfferingRepository;
import com.societycare.repository.SocietyRepository;
import com.societycare.service.impl.CampaignServiceImpl;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignServiceTest {

    @Mock
    private BookingCampaignRepository campaignRepository;
    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;
    @Mock
    private SocietyRepository societyRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private CampaignServiceImpl campaignService;

    private Society society;
    private User admin;
    private User provider;
    private ServiceOffering offering;

    @BeforeEach
    void setUp() {
        society = new Society();
        society.setId(1L);
        society.setSocietyName("Green Valley");

        admin = user(1L, Role.ADMIN, null);
        provider = user(2L, Role.SERVICE_PROVIDER, society);

        offering = new ServiceOffering();
        offering.setId(7L);
        offering.setServiceName("AC Servicing");
        offering.setRegularPrice(new BigDecimal("1000.00"));
        offering.setServiceProvider(provider);
        offering.setSociety(society);
        offering.setActive(true);

        when(currentUserService.getCurrentUser()).thenReturn(admin);
        when(serviceOfferingRepository.findById(7L)).thenReturn(Optional.of(offering));
        when(societyRepository.findById(1L)).thenReturn(Optional.of(society));
        when(campaignRepository.save(any(BookingCampaign.class))).thenAnswer(inv -> {
            BookingCampaign c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
    }

    private User user(Long id, Role role, Society s) {
        User u = new User();
        u.setId(id);
        u.setFullName("User " + id);
        u.setRole(role);
        u.setSociety(s);
        return u;
    }

    private CampaignRequest request(Long offeringId, Long societyId, int min, Integer max,
                                    LocalDate start, LocalDate end) {
        return new CampaignRequest("AC Offer", "desc", offeringId, societyId, min, max,
                new BigDecimal("1000.00"), new BigDecimal("20.00"), start, end);
    }

    private CampaignRequest validRequest() {
        LocalDate today = LocalDate.now();
        return request(7L, 1L, 10, 25, today, today.plusDays(14));
    }

    @Test
    void createCampaignCalculatesDiscountedPriceAndStartsPending() {
        CampaignResponse response = campaignService.create(validRequest());

        assertThat(response.status()).isEqualTo(CampaignStatus.PENDING);
        assertThat(response.currentParticipants()).isZero();
        assertThat(response.regularPrice()).isEqualByComparingTo("1000.00");
        assertThat(response.discountedPrice()).isEqualByComparingTo("800.00");
        assertThat(response.minimumParticipants()).isEqualTo(10);
        assertThat(response.maximumParticipants()).isEqualTo(25);
    }

    @Test
    void createCampaignAllowsProviderForOwnOffering() {
        when(currentUserService.getCurrentUser()).thenReturn(provider);
        CampaignResponse response = campaignService.create(validRequest());
        assertThat(response.id()).isEqualTo(100L);
    }

    @Test
    void createCampaignRejectsProviderForSomeoneElsesOffering() {
        User otherProvider = user(3L, Role.SERVICE_PROVIDER, society);
        when(currentUserService.getCurrentUser()).thenReturn(otherProvider);

        assertThatThrownBy(() -> campaignService.create(validRequest()))
                .isInstanceOf(UnauthorizedOperationException.class);
        verify(campaignRepository, never()).save(any(BookingCampaign.class));
    }

    @Test
    void createCampaignFailsForUnknownServiceOffering() {
        LocalDate today = LocalDate.now();
        assertThatThrownBy(() -> campaignService.create(request(99L, 1L, 10, null, today, today.plusDays(5))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCampaignFailsForUnknownSociety() {
        LocalDate today = LocalDate.now();
        assertThatThrownBy(() -> campaignService.create(request(7L, 99L, 10, null, today, today.plusDays(5))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCampaignRejectsEndDateBeforeStartDate() {
        LocalDate today = LocalDate.now();
        assertThatThrownBy(() -> campaignService.create(request(7L, 1L, 10, null, today.plusDays(5), today.plusDays(2))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("End date");
    }

    @Test
    void createCampaignRejectsMaximumBelowMinimum() {
        LocalDate today = LocalDate.now();
        assertThatThrownBy(() -> campaignService.create(request(7L, 1L, 10, 5, today, today.plusDays(5))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Maximum");
    }

    @Test
    void createCampaignRejectsOfferingFromAnotherSociety() {
        Society other = new Society();
        other.setId(2L);
        when(societyRepository.findById(2L)).thenReturn(Optional.of(other));
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> campaignService.create(request(7L, 2L, 10, null, today, today.plusDays(5))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createCampaignRejectsInactiveOffering() {
        offering.setActive(false);
        assertThatThrownBy(() -> campaignService.create(validRequest()))
                .isInstanceOf(BusinessRuleException.class);
    }

    private BookingCampaign campaignWithStatus(CampaignStatus status) {
        BookingCampaign c = new BookingCampaign();
        c.setId(100L);
        c.setServiceOffering(offering);
        c.setSociety(society);
        c.setStatus(status);
        c.setCurrentParticipants(10);
        c.setMinimumParticipants(10);
        c.setRegularPrice(new BigDecimal("1000.00"));
        c.setDiscountPercentage(new BigDecimal("20.00"));
        c.setDiscountedPrice(new BigDecimal("800.00"));
        c.setStartDate(LocalDate.now());
        c.setEndDate(LocalDate.now().plusDays(5));
        return c;
    }

    @Test
    void confirmedCampaignCanBeCompletedByOwningProvider() {
        when(currentUserService.getCurrentUser()).thenReturn(provider);
        when(campaignRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(campaignWithStatus(CampaignStatus.CONFIRMED)));

        CampaignResponse response = campaignService.complete(100L);

        assertThat(response.status()).isEqualTo(CampaignStatus.COMPLETED);
    }

    @Test
    void pendingCampaignCannotBeCompleted() {
        when(campaignRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(campaignWithStatus(CampaignStatus.PENDING)));

        assertThatThrownBy(() -> campaignService.complete(100L))
                .isInstanceOf(InvalidCampaignStateException.class);
    }

    @Test
    void expiredCampaignCannotBeCompleted() {
        when(campaignRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(campaignWithStatus(CampaignStatus.EXPIRED)));

        assertThatThrownBy(() -> campaignService.complete(100L))
                .isInstanceOf(InvalidCampaignStateException.class);
    }

    @Test
    void anotherProviderCannotCompleteTheCampaign() {
        when(currentUserService.getCurrentUser()).thenReturn(user(3L, Role.SERVICE_PROVIDER, society));
        when(campaignRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(campaignWithStatus(CampaignStatus.CONFIRMED)));

        assertThatThrownBy(() -> campaignService.complete(100L))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void invalidTransitionsAreRejectedByTheStatusEnum() {
        assertThat(CampaignStatus.PENDING.canTransitionTo(CampaignStatus.CONFIRMED)).isTrue();
        assertThat(CampaignStatus.PENDING.canTransitionTo(CampaignStatus.EXPIRED)).isTrue();
        assertThat(CampaignStatus.PENDING.canTransitionTo(CampaignStatus.COMPLETED)).isFalse();
        assertThat(CampaignStatus.CONFIRMED.canTransitionTo(CampaignStatus.COMPLETED)).isTrue();
        assertThat(CampaignStatus.CONFIRMED.canTransitionTo(CampaignStatus.EXPIRED)).isFalse();
        assertThat(CampaignStatus.EXPIRED.canTransitionTo(CampaignStatus.CONFIRMED)).isFalse();
        assertThat(CampaignStatus.COMPLETED.canTransitionTo(CampaignStatus.PENDING)).isFalse();
    }

    @Test
    void expireOverdueCampaignsReturnsNumberExpired() {
        when(campaignRepository.expireOverdue(any(LocalDate.class))).thenReturn(3);
        assertThat(campaignService.expireOverdueCampaigns()).isEqualTo(3);
    }
}
