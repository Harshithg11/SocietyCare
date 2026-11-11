package com.societycare.entity;

import com.societycare.enums.CampaignStatus;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A group-booking campaign. regularPrice, discountPercentage and discountedPrice are SNAPSHOTS stored on the
 * campaign, so later edits to the service offering never change the price of an existing campaign.
 * currentParticipants is only changed while holding a PESSIMISTIC_WRITE lock on this row (see BookingServiceImpl);
 * @Version is an additional safety net for concurrent admin edits.
 */
@Entity
@Table(name = "booking_campaigns",
        indexes = {
                @Index(name = "idx_campaign_society", columnList = "society_id"),
                @Index(name = "idx_campaign_status", columnList = "status"),
                @Index(name = "idx_campaign_end_date", columnList = "end_date"),
                @Index(name = "idx_campaign_service", columnList = "service_offering_id")
        })
@Check(name = "chk_campaign_values",
        constraints = "minimum_participants > 0 AND current_participants >= 0 "
                + "AND (maximum_participants IS NULL OR maximum_participants >= minimum_participants) "
                + "AND end_date >= start_date")
@Getter
@Setter
@NoArgsConstructor
public class BookingCampaign extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_title", nullable = false, length = 150)
    private String campaignTitle;

    @Column(name = "description", length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_offering_id", nullable = false)
    private ServiceOffering serviceOffering;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "society_id", nullable = false)
    private Society society;

    @Column(name = "minimum_participants", nullable = false)
    private Integer minimumParticipants;

    /** Optional upper limit; null means unlimited. */
    @Column(name = "maximum_participants")
    private Integer maximumParticipants;

    @Column(name = "current_participants", nullable = false)
    private Integer currentParticipants = 0;

    @Column(name = "regular_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal regularPrice;

    @Column(name = "discount_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "discounted_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountedPrice;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.PENDING;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
