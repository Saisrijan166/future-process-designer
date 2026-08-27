package com.assesswise.processdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An edge between two claims: they agree, or they do not.
 *
 * <p>{@link #sameDomain} exists because it decides whether the edge means anything. Two pages on
 * one publisher's site repeating one another is not independent confirmation, and treating it as
 * such would inflate every confidence score in the system.
 */
@Entity
@Table(name = "claim_relation")
@Getter
@Setter
@NoArgsConstructor
public class ClaimRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_a_id", nullable = false)
    private EvidenceClaim claimA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_b_id", nullable = false)
    private EvidenceClaim claimB;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 14)
    private ClaimRelationType relationType;

    @Column(name = "similarity", nullable = false)
    private double similarity;

    @Column(name = "same_domain", nullable = false)
    private boolean sameDomain;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
