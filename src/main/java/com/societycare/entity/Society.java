package com.societycare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A residential society. Users, services and campaigns point to it (many-to-one).
 * There is deliberately no @OneToMany list here: it avoids lazy-loading and JSON-recursion
 * problems, and the data is queried through the repositories instead.
 */
@Entity
@Table(name = "societies",
        uniqueConstraints = @UniqueConstraint(name = "uk_society_name_pincode",
                columnNames = {"society_name", "pincode"}))
@Getter
@Setter
@NoArgsConstructor
public class Society extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "society_name", nullable = false, length = 150)
    private String societyName;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "pincode", nullable = false, length = 6)
    private String pincode;
}
