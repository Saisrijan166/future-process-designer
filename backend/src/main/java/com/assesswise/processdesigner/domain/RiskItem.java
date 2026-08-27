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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry in the risk register, with its controls and — where the research found one — the
 * obligation it derives from.
 *
 * <p>A redesign that hands candidate assessment to a model and does not enumerate what could go
 * wrong is not a design, it is a pitch. Risks cite evidence claims like opportunities do, so
 * "India's DPDP Act requires verifiable consent for biometric processing" appears with the quote
 * and the source it came from rather than as an unattributed assertion.
 */
@Entity
@Table(name = "risk_item")
@Getter
@Setter
@NoArgsConstructor
public class RiskItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private BusinessProcess process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_opportunity_id")
    private AiOpportunity opportunity;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private RiskCategory category = RiskCategory.OPERATIONAL;

    @Column(name = "likelihood", nullable = false)
    private short likelihood;

    @Column(name = "impact", nullable = false)
    private short impact;

    /** likelihood x impact, stored so the register can be sorted in SQL. */
    @Column(name = "severity_score", nullable = false)
    private int severityScore;

    @Column(name = "mitigation", columnDefinition = "text")
    private String mitigation;

    /** Who owns the control. A risk with no owner is a risk nobody is managing. */
    @Column(name = "owner_role", length = 150)
    private String ownerRole;

    /** The specific legal or standards obligation, where one applies. */
    @Column(name = "obligation", length = 400)
    private String obligation;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "risk_item_claim",
            joinColumns = @JoinColumn(name = "risk_item_id"),
            inverseJoinColumns = @JoinColumn(name = "evidence_claim_id"))
    private Set<EvidenceClaim> citedClaims = new LinkedHashSet<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
