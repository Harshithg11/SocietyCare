package com.societycare.service;

import com.societycare.dto.request.SocietyRequest;
import com.societycare.dto.response.SocietyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SocietyService {

    SocietyResponse create(SocietyRequest request);

    Page<SocietyResponse> getAll(Pageable pageable);

    SocietyResponse getById(Long id);

    SocietyResponse update(Long id, SocietyRequest request);

    void delete(Long id);
}
