package com.societycare.service.impl;

import com.societycare.dto.request.ServiceOfferingRequest;
import com.societycare.dto.response.ServiceOfferingResponse;
import com.societycare.entity.ServiceOffering;
import com.societycare.entity.Society;
import com.societycare.entity.User;
import com.societycare.enums.Role;
import com.societycare.exception.BusinessRuleException;
import com.societycare.exception.ResourceNotFoundException;
import com.societycare.exception.UnauthorizedOperationException;
import com.societycare.repository.BookingCampaignRepository;
import com.societycare.repository.ServiceOfferingRepository;
import com.societycare.repository.SocietyRepository;
import com.societycare.repository.UserRepository;
import com.societycare.service.CurrentUserService;
import com.societycare.service.ServiceOfferingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceOfferingServiceImpl implements ServiceOfferingService {

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookingCampaignRepository campaignRepository;
    private final SocietyRepository societyRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public ServiceOfferingResponse create(ServiceOfferingRequest request) {
        User actor = currentUserService.getCurrentUser();

        User provider;
        Society society;
        if (actor.getRole() == Role.SERVICE_PROVIDER) {
            // Providers always create offerings for their own society under their own account.
            provider = actor;
            society = actor.getSociety();
            if (society == null) {
                throw new BusinessRuleException("Your account is not linked to a society.");
            }
        } else {
            // ADMIN must say who owns the offering and where it is offered.
            if (request.providerId() == null || request.societyId() == null) {
                throw new BusinessRuleException("providerId and societyId are required when an admin creates a service.");
            }
            provider = userRepository.findById(request.providerId())
                    .filter(u -> u.getRole() == Role.SERVICE_PROVIDER)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Service provider not found with id " + request.providerId()));
            society = societyRepository.findById(request.societyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Society not found with id " + request.societyId()));
            if (provider.getSociety() == null || !provider.getSociety().getId().equals(society.getId())) {
                throw new BusinessRuleException("The service provider does not belong to the selected society.");
            }
        }

        ServiceOffering offering = new ServiceOffering();
        applyFields(offering, request);
        offering.setActive(request.active() == null || request.active());
        offering.setServiceProvider(provider);
        offering.setSociety(society);
        return ServiceOfferingResponse.from(serviceOfferingRepository.save(offering));
    }

    /** CITIZEN: active services of their society. SERVICE_PROVIDER: own services. ADMIN: everything. */
    @Override
    public Page<ServiceOfferingResponse> getAll(Pageable pageable) {
        User actor = currentUserService.getCurrentUser();
        Page<ServiceOffering> page = switch (actor.getRole()) {
            case ADMIN -> serviceOfferingRepository.findAll(pageable);
            case SERVICE_PROVIDER -> serviceOfferingRepository.findByServiceProviderId(actor.getId(), pageable);
            case CITIZEN -> {
                if (actor.getSociety() == null) {
                    throw new BusinessRuleException("Your account is not linked to a society.");
                }
                yield serviceOfferingRepository.findBySocietyIdAndActiveTrue(actor.getSociety().getId(), pageable);
            }
        };
        return page.map(ServiceOfferingResponse::from);
    }

    @Override
    public ServiceOfferingResponse getById(Long id) {
        User actor = currentUserService.getCurrentUser();
        ServiceOffering offering = findOrThrow(id);
        boolean allowed = switch (actor.getRole()) {
            case ADMIN -> true;
            case SERVICE_PROVIDER -> offering.isProvidedBy(actor);
            case CITIZEN -> offering.isActive() && actor.getSociety() != null
                    && actor.getSociety().getId().equals(offering.getSociety().getId());
        };
        if (!allowed) {
            throw new UnauthorizedOperationException("You are not allowed to view this service offering.");
        }
        return ServiceOfferingResponse.from(offering);
    }

    /** Price changes never affect existing campaigns because campaigns store their own price snapshot. */
    @Override
    @Transactional
    public ServiceOfferingResponse update(Long id, ServiceOfferingRequest request) {
        User actor = currentUserService.getCurrentUser();
        ServiceOffering offering = findOrThrow(id);
        assertCanManage(offering, actor);

        applyFields(offering, request);
        if (request.active() != null) {
            offering.setActive(request.active());
        }
        return ServiceOfferingResponse.from(serviceOfferingRepository.save(offering));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User actor = currentUserService.getCurrentUser();
        ServiceOffering offering = findOrThrow(id);
        assertCanManage(offering, actor);
        if (campaignRepository.existsByServiceOfferingId(id)) {
            throw new BusinessRuleException(
                    "This service is used by one or more campaigns and cannot be deleted. "
                            + "Set it to inactive instead.", HttpStatus.CONFLICT);
        }
        serviceOfferingRepository.delete(offering);
    }

    private void applyFields(ServiceOffering offering, ServiceOfferingRequest request) {
        offering.setServiceName(request.serviceName().trim());
        offering.setDescription(request.description());
        offering.setCategory(request.category());
        offering.setRegularPrice(request.regularPrice());
    }

    private ServiceOffering findOrThrow(Long id) {
        return serviceOfferingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service offering not found with id " + id));
    }

    private void assertCanManage(ServiceOffering offering, User actor) {
        boolean allowed = actor.getRole() == Role.ADMIN
                || (actor.getRole() == Role.SERVICE_PROVIDER && offering.isProvidedBy(actor));
        if (!allowed) {
            throw new UnauthorizedOperationException("You are not allowed to manage this service offering.");
        }
    }
}
