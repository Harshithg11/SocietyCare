package com.societycare.repository;

import com.societycare.entity.Booking;
import com.societycare.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByCitizenIdAndBookingCampaignId(Long citizenId, Long campaignId);

    boolean existsByBookingCampaignId(Long campaignId);

    long countByBookingCampaignIdAndBookingStatus(Long campaignId, BookingStatus status);

    /** Scalar query: does NOT load the Booking entity (used before locking the campaign). */
    @Query("select b.bookingCampaign.id from Booking b where b.id = :id")
    Optional<Long> findCampaignIdById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"citizen", "bookingCampaign", "bookingCampaign.serviceOffering",
            "bookingCampaign.society"})
    Page<Booking> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"citizen", "bookingCampaign", "bookingCampaign.serviceOffering",
            "bookingCampaign.society"})
    Page<Booking> findByCitizenId(Long citizenId, Pageable pageable);

    @EntityGraph(attributePaths = {"citizen", "bookingCampaign", "bookingCampaign.serviceOffering",
            "bookingCampaign.society"})
    Page<Booking> findByBookingCampaignId(Long campaignId, Pageable pageable);

    @EntityGraph(attributePaths = {"citizen", "bookingCampaign", "bookingCampaign.serviceOffering",
            "bookingCampaign.society"})
    Page<Booking> findByBookingCampaignServiceOfferingServiceProviderId(Long providerId, Pageable pageable);

    @EntityGraph(attributePaths = {"citizen"})
    Page<Booking> findByBookingCampaignIdAndBookingStatus(Long campaignId, BookingStatus status, Pageable pageable);
}
