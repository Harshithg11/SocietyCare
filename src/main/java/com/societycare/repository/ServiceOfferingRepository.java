package com.societycare.repository;

import com.societycare.entity.ServiceOffering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    @Override
    @EntityGraph(attributePaths = {"serviceProvider", "society"})
    Page<ServiceOffering> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"serviceProvider", "society"})
    Page<ServiceOffering> findBySocietyIdAndActiveTrue(Long societyId, Pageable pageable);

    @EntityGraph(attributePaths = {"serviceProvider", "society"})
    Page<ServiceOffering> findByServiceProviderId(Long providerId, Pageable pageable);

    boolean existsBySocietyId(Long societyId);
}
