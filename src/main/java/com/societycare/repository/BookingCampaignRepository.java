package com.societycare.repository;

import com.societycare.entity.BookingCampaign;
import com.societycare.enums.CampaignStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface BookingCampaignRepository extends JpaRepository<BookingCampaign, Long> {

    /**
     * SELECT ... FOR UPDATE. Every operation that changes participants or status
     * (join, cancel, update, complete, delete) must load the campaign through this method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookingCampaign c where c.id = :id")
    Optional<BookingCampaign> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"serviceOffering", "society"})
    Page<BookingCampaign> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"serviceOffering", "society"})
    Page<BookingCampaign> findBySocietyId(Long societyId, Pageable pageable);

    @EntityGraph(attributePaths = {"serviceOffering", "society"})
    Page<BookingCampaign> findBySocietyIdAndStatus(Long societyId, CampaignStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"serviceOffering", "society"})
    Page<BookingCampaign> findByStatus(CampaignStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"serviceOffering", "society"})
    Page<BookingCampaign> findByServiceOfferingServiceProviderId(Long providerId, Pageable pageable);

    @EntityGraph(attributePaths = {"serviceOffering", "society"})
    Page<BookingCampaign> findByServiceOfferingServiceProviderIdAndStatus(Long providerId, CampaignStatus status,
                                                                         Pageable pageable);

    @EntityGraph(attributePaths = {"serviceOffering", "society"})
    Page<BookingCampaign> findBySocietyIdAndServiceOfferingServiceProviderId(Long societyId, Long providerId,
                                                                            Pageable pageable);

    boolean existsByServiceOfferingId(Long serviceOfferingId);

    boolean existsBySocietyId(Long societyId);

    /**
     * Bulk-expires PENDING campaigns whose end date has passed. The UPDATE takes row locks, so concurrent joins wait
     * and then see the new status. The version is bumped manually because bulk JPQL skips optimistic-lock handling.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingCampaign c set c.status = com.societycare.enums.CampaignStatus.EXPIRED, "
            + "c.version = c.version + 1 "
            + "where c.status = com.societycare.enums.CampaignStatus.PENDING and c.endDate < :today")
    int expireOverdue(@Param("today") LocalDate today);
}
