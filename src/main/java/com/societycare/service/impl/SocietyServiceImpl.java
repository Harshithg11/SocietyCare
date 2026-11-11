package com.societycare.service.impl;

import com.societycare.dto.request.SocietyRequest;
import com.societycare.dto.response.SocietyResponse;
import com.societycare.entity.Society;
import com.societycare.exception.BusinessRuleException;
import com.societycare.exception.DuplicateResourceException;
import com.societycare.exception.ResourceNotFoundException;
import com.societycare.repository.BookingCampaignRepository;
import com.societycare.repository.ServiceOfferingRepository;
import com.societycare.repository.SocietyRepository;
import com.societycare.repository.UserRepository;
import com.societycare.service.SocietyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocietyServiceImpl implements SocietyService {

    private final SocietyRepository societyRepository;
    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookingCampaignRepository campaignRepository;

    @Override
    @Transactional
    public SocietyResponse create(SocietyRequest request) {
        String name = request.societyName().trim();
        if (societyRepository.existsBySocietyNameIgnoreCaseAndPincode(name, request.pincode())) {
            throw new DuplicateResourceException("A society with this name already exists for this pincode.");
        }
        Society society = new Society();
        apply(society, request);
        return SocietyResponse.from(societyRepository.save(society));
    }

    @Override
    public Page<SocietyResponse> getAll(Pageable pageable) {
        return societyRepository.findAll(pageable).map(SocietyResponse::from);
    }

    @Override
    public SocietyResponse getById(Long id) {
        return SocietyResponse.from(findOrThrow(id));
    }

    @Override
    @Transactional
    public SocietyResponse update(Long id, SocietyRequest request) {
        Society society = findOrThrow(id);
        String name = request.societyName().trim();
        boolean identityChanged = !society.getSocietyName().equalsIgnoreCase(name)
                || !society.getPincode().equals(request.pincode());
        if (identityChanged && societyRepository.existsBySocietyNameIgnoreCaseAndPincode(name, request.pincode())) {
            throw new DuplicateResourceException("A society with this name already exists for this pincode.");
        }
        apply(society, request);
        return SocietyResponse.from(societyRepository.save(society));
    }

    /** A society that still has users, services or campaigns cannot be deleted. */
    @Override
    @Transactional
    public void delete(Long id) {
        Society society = findOrThrow(id);
        if (userRepository.existsBySocietyId(id)
                || serviceOfferingRepository.existsBySocietyId(id)
                || campaignRepository.existsBySocietyId(id)) {
            throw new BusinessRuleException(
                    "This society still has users, services or campaigns and cannot be deleted.",
                    HttpStatus.CONFLICT);
        }
        societyRepository.delete(society);
    }

    private void apply(Society society, SocietyRequest request) {
        society.setSocietyName(request.societyName().trim());
        society.setAddress(request.address().trim());
        society.setCity(request.city().trim());
        society.setState(request.state().trim());
        society.setPincode(request.pincode());
    }

    private Society findOrThrow(Long id) {
        return societyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Society not found with id " + id));
    }
}
