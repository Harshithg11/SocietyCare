package com.societycare.dto.response;

import com.societycare.entity.ServiceOffering;
import com.societycare.enums.ServiceCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceOfferingResponse(
        Long id,
        String serviceName,
        String description,
        ServiceCategory category,
        BigDecimal regularPrice,
        Long serviceProviderId,
        String serviceProviderName,
        Long societyId,
        String societyName,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ServiceOfferingResponse from(ServiceOffering s) {
        return new ServiceOfferingResponse(
                s.getId(),
                s.getServiceName(),
                s.getDescription(),
                s.getCategory(),
                s.getRegularPrice(),
                s.getServiceProvider().getId(),
                s.getServiceProvider().getFullName(),
                s.getSociety().getId(),
                s.getSociety().getSocietyName(),
                s.isActive(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
