package com.societycare.service;

import com.societycare.dto.request.CampaignRequest;
import com.societycare.dto.request.CampaignUpdateRequest;
import com.societycare.dto.response.CampaignResponse;
import com.societycare.dto.response.ParticipantResponse;
import com.societycare.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CampaignService {

    CampaignResponse create(CampaignRequest request);

    Page<CampaignResponse> getAll(Pageable pageable);

    CampaignResponse getById(Long id);

    Page<CampaignResponse> getBySociety(Long societyId, Pageable pageable);

    Page<CampaignResponse> getByStatus(CampaignStatus status, Pageable pageable);

    CampaignResponse update(Long id, CampaignUpdateRequest request);

    void delete(Long id);

    CampaignResponse complete(Long id);

    Page<ParticipantResponse> getParticipants(Long id, Pageable pageable);

    /** Expires every PENDING campaign whose end date has passed. Returns how many were expired. */
    int expireOverdueCampaigns();
}
