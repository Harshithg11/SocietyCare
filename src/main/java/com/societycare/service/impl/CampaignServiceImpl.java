package com.societycare.service.impl;

import com.societycare.dto.request.CampaignRequest;
import com.societycare.dto.request.CampaignUpdateRequest;
import com.societycare.dto.response.CampaignResponse;
import com.societycare.dto.response.ParticipantResponse;
import com.societycare.entity.BookingCampaign;
import com.societycare.entity.ServiceOffering;
import com.societycare.entity.Society;
import com.societycare.entity.User;
import com.societycare.enums.BookingStatus;
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
import com.societycare.service.CampaignService;
import com.societycare.service.CurrentUserService;
import com.societycare.util.PriceCalculator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignServiceImpl implements CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignServiceImpl.class);

    private final BookingCampaignRepository campaignRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final SocietyRepository societyRepository;
    private final BookingRepository bookingRepository;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public CampaignResponse create(CampaignRequest request) {
        User actor = currentUserService.getCurrentUser();

        ServiceOffering offering = serviceOfferingRepository.findById(request.serviceOfferingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service offering not found with id " + request.serviceOfferingId()));
        Society society = societyRepository.findById(request.societyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Society not found with id " + request.societyId()));

        if (actor.getRole() == Role.SERVICE_PROVIDER && !offering.isProvidedBy(actor)) {
            throw new UnauthorizedOperationException("You can only create campaigns for your own service offerings.");
        }
        if (!offering.isActive()) {
            throw new BusinessRuleException("The service offering is not active.");
        }
        if (!offering.getSociety().getId().equals(society.getId())) {
            throw new BusinessRuleException("The service offering is not offered in the selected society.");
        }
        validateDates(request.startDate(), request.endDate());
        validateParticipants(request.minimumParticipants(), request.maximumParticipants(), 0);

        BookingCampaign campaign = new BookingCampaign();
        campaign.setServiceOffering(offering);
        campaign.setSociety(society);
        campaign.setCurrentParticipants(0);
        campaign.setStatus(CampaignStatus.PENDING);
        campaign.setCampaignTitle(request.campaignTitle().trim());
        campaign.setDescription(request.description());
        campaign.setMinimumParticipants(request.minimumParticipants());
        campaign.setMaximumParticipants(request.maximumParticipants());
        campaign.setRegularPrice(request.regularPrice());
        campaign.setDiscountPercentage(request.discountPercentage());
        // Stored as a snapshot: later changes to the service offering do not alter this campaign's price.
        campaign.setDiscountedPrice(PriceCalculator.discountedPrice(request.regularPrice(), request.discountPercentage()));
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());

        BookingCampaign saved = campaignRepository.save(campaign);
        log.info("Campaign {} created for society {}", saved.getId(), society.getId());
        return CampaignResponse.from(saved);
    }

    /** ADMIN: all. SERVICE_PROVIDER: campaigns of own services. CITIZEN: campaigns of own society. */
    @Override
    public Page<CampaignResponse> getAll(Pageable pageable) {
        return scopedPage(currentUserService.getCurrentUser(), null, pageable).map(CampaignResponse::from);
    }

    @Override
    public Page<CampaignResponse> getByStatus(CampaignStatus status, Pageable pageable) {
        return scopedPage(currentUserService.getCurrentUser(), status, pageable).map(CampaignResponse::from);
    }

    @Override
    public CampaignResponse getById(Long id) {
        User actor = currentUserService.getCurrentUser();
        BookingCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id " + id));
        boolean allowed = switch (actor.getRole()) {
            case ADMIN -> true;
            case SERVICE_PROVIDER -> campaign.getServiceOffering().isProvidedBy(actor);
            case CITIZEN -> actor.getSociety() != null
                    && actor.getSociety().getId().equals(campaign.getSociety().getId());
        };
        if (!allowed) {
            throw new UnauthorizedOperationException("You are not allowed to view this campaign.");
        }
        return CampaignResponse.from(campaign);
    }

    @Override
    public Page<CampaignResponse> getBySociety(Long societyId, Pageable pageable) {
        User actor = currentUserService.getCurrentUser();
        Page<BookingCampaign> page;
        switch (actor.getRole()) {
            case CITIZEN -> {
                if (actor.getSociety() == null || !actor.getSociety().getId().equals(societyId)) {
                    throw new UnauthorizedOperationException("You can only view campaigns of your own society.");
                }
                page = campaignRepository.findBySocietyId(societyId, pageable);
            }
            case SERVICE_PROVIDER -> page = campaignRepository
                    .findBySocietyIdAndServiceOfferingServiceProviderId(societyId, actor.getId(), pageable);
            default -> {
                if (!societyRepository.existsById(societyId)) {
                    throw new ResourceNotFoundException("Society not found with id " + societyId);
                }
                page = campaignRepository.findBySocietyId(societyId, pageable);
            }
        }
        return page.map(CampaignResponse::from);
    }

    /** Only PENDING campaigns can be edited. The row is locked so edits cannot interleave with joins. */
    @Override
    @Transactional
    public CampaignResponse update(Long id, CampaignUpdateRequest request) {
        User actor = currentUserService.getCurrentUser();
        BookingCampaign campaign = lockOrThrow(id);
        assertCanManage(campaign, actor);

        if (campaign.getStatus() != CampaignStatus.PENDING) {
            throw new InvalidCampaignStateException("Only PENDING campaigns can be edited.");
        }
        LocalDate today = LocalDate.now();
        boolean alreadyStarted = !campaign.getStartDate().isAfter(today);
        if (alreadyStarted && !campaign.getStartDate().equals(request.startDate())) {
            throw new BusinessRuleException("The start date cannot be changed after the campaign has started.");
        }
        if (!alreadyStarted && request.startDate().isBefore(today)) {
            throw new BusinessRuleException("Start date cannot be in the past.");
        }
        if (request.endDate().isBefore(today)) {
            throw new BusinessRuleException("End date cannot be in the past.");
        }
        validateDates(request.startDate(), request.endDate());
        // A PENDING campaign always has fewer participants than the minimum, so the new minimum must stay above them.
        validateParticipants(request.minimumParticipants(), request.maximumParticipants(),
                campaign.getCurrentParticipants());
        if (request.minimumParticipants() <= campaign.getCurrentParticipants()) {
            throw new BusinessRuleException(
                    "Minimum participants must be greater than the current participant count ("
                            + campaign.getCurrentParticipants() + ").");
        }

        campaign.setCampaignTitle(request.campaignTitle().trim());
        campaign.setDescription(request.description());
        campaign.setMinimumParticipants(request.minimumParticipants());
        campaign.setMaximumParticipants(request.maximumParticipants());
        campaign.setRegularPrice(request.regularPrice());
        campaign.setDiscountPercentage(request.discountPercentage());
        campaign.setDiscountedPrice(PriceCalculator.discountedPrice(request.regularPrice(), request.discountPercentage()));
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());
        return CampaignResponse.from(campaignRepository.save(campaign));
    }

    /** A campaign that has any booking (even cancelled) is kept for history and cannot be deleted. */
    @Override
    @Transactional
    public void delete(Long id) {
        User actor = currentUserService.getCurrentUser();
        BookingCampaign campaign = lockOrThrow(id);
        assertCanManage(campaign, actor);
        if (bookingRepository.existsByBookingCampaignId(id)) {
            throw new BusinessRuleException("This campaign already has bookings and cannot be deleted.",
                    HttpStatus.CONFLICT);
        }
        campaignRepository.delete(campaign);
    }

    /** CONFIRMED -> COMPLETED, by the owning provider or an admin. */
    @Override
    @Transactional
    public CampaignResponse complete(Long id) {
        User actor = currentUserService.getCurrentUser();
        BookingCampaign campaign = lockOrThrow(id);
        assertCanManage(campaign, actor);

        if (!campaign.getStatus().canTransitionTo(CampaignStatus.COMPLETED)) {
            throw new InvalidCampaignStateException("Only CONFIRMED campaigns can be completed. This campaign is "
                    + campaign.getStatus() + ".");
        }
        campaign.setStatus(CampaignStatus.COMPLETED);
        return CampaignResponse.from(campaignRepository.save(campaign));
    }

    @Override
    public Page<ParticipantResponse> getParticipants(Long id, Pageable pageable) {
        User actor = currentUserService.getCurrentUser();
        BookingCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id " + id));
        assertCanManage(campaign, actor);
        return bookingRepository.findByBookingCampaignIdAndBookingStatus(id, BookingStatus.BOOKED, pageable)
                .map(ParticipantResponse::from);
    }

    @Override
    @Transactional
    public int expireOverdueCampaigns() {
        return campaignRepository.expireOverdue(LocalDate.now());
    }

    // ---------------- helpers ----------------

    private Page<BookingCampaign> scopedPage(User actor, CampaignStatus status, Pageable pageable) {
        return switch (actor.getRole()) {
            case ADMIN -> status == null
                    ? campaignRepository.findAll(pageable)
                    : campaignRepository.findByStatus(status, pageable);
            case SERVICE_PROVIDER -> status == null
                    ? campaignRepository.findByServiceOfferingServiceProviderId(actor.getId(), pageable)
                    : campaignRepository.findByServiceOfferingServiceProviderIdAndStatus(actor.getId(), status, pageable);
            case CITIZEN -> {
                if (actor.getSociety() == null) {
                    throw new BusinessRuleException("Your account is not linked to a society.");
                }
                Long societyId = actor.getSociety().getId();
                yield status == null
                        ? campaignRepository.findBySocietyId(societyId, pageable)
                        : campaignRepository.findBySocietyIdAndStatus(societyId, status, pageable);
            }
        };
    }

    private BookingCampaign lockOrThrow(Long id) {
        return campaignRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id " + id));
    }

    private void assertCanManage(BookingCampaign campaign, User actor) {
        boolean allowed = actor.getRole() == Role.ADMIN
                || (actor.getRole() == Role.SERVICE_PROVIDER && campaign.getServiceOffering().isProvidedBy(actor));
        if (!allowed) {
            throw new UnauthorizedOperationException("You are not allowed to manage this campaign.");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleException("End date must not be before the start date.");
        }
    }

    private void validateParticipants(Integer min, Integer max, int current) {
        if (max != null && max < min) {
            throw new BusinessRuleException("Maximum participants must be greater than or equal to the minimum.");
        }
        if (max != null && max < current) {
            throw new BusinessRuleException("Maximum participants cannot be less than the current participant count.");
        }
    }
}
