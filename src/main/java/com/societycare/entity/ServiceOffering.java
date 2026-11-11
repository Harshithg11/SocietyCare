package com.societycare.entity;

import com.societycare.enums.ServiceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

@Entity
@Table(name = "service_offerings",
        indexes = {
                @Index(name = "idx_service_society", columnList = "society_id"),
                @Index(name = "idx_service_provider", columnList = "service_provider_id")
        })
@Check(name = "chk_service_regular_price_positive", constraints = "regular_price > 0")
@Getter
@Setter
@NoArgsConstructor
public class ServiceOffering extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, length = 150)
    private String serviceName;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ServiceCategory category;

    @Column(name = "regular_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal regularPrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_provider_id", nullable = false)
    private User serviceProvider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "society_id", nullable = false)
    private Society society;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public boolean isProvidedBy(User user) {
        return user != null && serviceProvider != null && serviceProvider.getId().equals(user.getId());
    }
}
