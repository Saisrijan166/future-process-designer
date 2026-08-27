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
 * What one intervention is worth per month, and the arithmetic behind it.
 *
 * <p>Inputs live next to outputs deliberately. "Saves 1,240 hours a month" is a claim; "8,000 items
 * a month at 14 minutes each, 62% of that handled by AI" is a claim someone can argue with, which
 * is the only kind worth putting in front of a decision maker. {@link #basis} records whether the
 * inputs were estimated by a model or supplied by a person, and the UI never renders the two the
 * same way.
 *
 * <p>Money is INR because that is the operating currency of the organisation being modelled.
 */
@Entity
@Table(name = "impact_estimate")
@Getter
@Setter
@NoArgsConstructor
public class ImpactEstimate {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(name = "label", nullable = false, length = 250)
    private String label;

    @Column(name = "volume_per_month", nullable = false)
    private double volumePerMonth;

    @Column(name = "minutes_per_item", nullable = false)
    private double minutesPerItem;

    /** Fraction of the work the intervention actually takes over, 0..1. Never assumed to be 1. */
    @Column(name = "automation_share", nullable = false)
    private double automationShare;

    @Column(name = "hourly_cost_inr", nullable = false)
    private double hourlyCostInr;

    @Column(name = "hours_saved_per_month", nullable = false)
    private double hoursSavedPerMonth;

    @Column(name = "cost_saved_per_month_inr", nullable = false)
    private double costSavedPerMonthInr;

    @Column(name = "error_reduction_percent")
    private Double errorReductionPercent;

    @Column(name = "one_off_effort_days")
    private Double oneOffEffortDays;

    /** Inference and licence cost of running it, so the saving is net rather than gross. */
    @Column(name = "run_cost_per_month_inr")
    private Double runCostPerMonthInr;

    @Column(name = "payback_months")
    private Double paybackMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, length = 16)
    private EstimateBasis basis = EstimateBasis.MODEL_ESTIMATE;

    /** Every assumption made, in plain words, one per line. */
    @Column(name = "assumptions", columnDefinition = "text")
    private String assumptions;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
