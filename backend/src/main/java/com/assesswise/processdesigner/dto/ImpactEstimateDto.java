package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.EstimateBasis;
import java.util.UUID;

/**
 * One line of the business case, with the inputs it was computed from.
 *
 * <p>The inputs travel with the outputs on purpose. "Saves 1,240 hours a month" is unarguable in the
 * unhelpful sense; "8,000 items a month, 14 minutes each, 55% of that removed" can be argued with,
 * and {@code basis} says whether a person supplied those figures or a model estimated them.
 */
public record ImpactEstimateDto(
        UUID id,
        UUID opportunityId,
        UUID activityId,
        String label,
        double volumePerMonth,
        double minutesPerItem,
        double automationShare,
        double hourlyCostInr,
        double hoursSavedPerMonth,
        double costSavedPerMonthInr,
        Double errorReductionPercent,
        Double oneOffEffortDays,
        Double runCostPerMonthInr,
        Double paybackMonths,
        EstimateBasis basis,
        String assumptions) {}
