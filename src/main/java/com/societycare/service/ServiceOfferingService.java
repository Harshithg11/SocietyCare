package com.societycare.service;

import com.societycare.dto.request.ServiceOfferingRequest;
import com.societycare.dto.response.ServiceOfferingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ServiceOfferingService {

    ServiceOfferingResponse create(ServiceOfferingRequest request);

    Page<ServiceOfferingResponse> getAll(Pageable pageable);

    ServiceOfferingResponse getById(Long id);

    ServiceOfferingResponse update(Long id, ServiceOfferingRequest request);

    void delete(Long id);
}
