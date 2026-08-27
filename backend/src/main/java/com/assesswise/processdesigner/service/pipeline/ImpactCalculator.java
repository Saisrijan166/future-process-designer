package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.service.NormalizedAnalysis;
import org.springframework.stereotype.Component;

/**
 * The arithmetic behind the business case.
 *
 * <p>Separate from the stage that gathers the inputs, and deliberately so: this is the part that must
 * be the same every time and checkable by hand. A model supplies volume, handling time, automation
 * share and hourly cost; everything below is multiplication a reader can redo on paper, which is the
 * only reason the resulting figure is worth putting in front of anyone.
 *
 * <p>Two decisions here are judgement calls rather than arithmetic, and both are made conservatively:
 *
 * <ul>
 *   <li><b>Savings are net.</b> Running cost is subtracted before payback is computed. A gross saving
 *       that ignores inference and licence cost is the oldest way to make a bad case look good.
 *   <li><b>Build cost uses the same hourly rate as the work being saved, at eight hours a day.</b>
 *       It understates the cost of engineering time, which biases payback in the pessimistic
 *       direction. That is the right direction to be wrong in.
 * </ul>
 */
@Component
public class ImpactCalculator {

    private static final double WORKING_HOURS_PER_DAY = 8;
    /** Beyond this a payback figure is not information, it is discouragement in numeric form. */
    private static final double MAX_MEANINGFUL_PAYBACK_MONTHS = 60;

    /**
     * @param hoursSavedPerMonth person-hours the intervention removes in a month
     * @param netSavingPerMonthInr saving after the cost of running the thing
     * @param paybackMonths null when there is no net saving to pay anything back with
     */
    public record Computed(
            double hoursSavedPerMonth,
            double grossSavingPerMonthInr,
            double runCostPerMonthInr,
            double netSavingPerMonthInr,
            double oneOffCostInr,
            Double paybackMonths) {}

    public Computed compute(NormalizedAnalysis.Impact impact) {
        double hoursSaved = impact.volumePerMonth() * impact.minutesPerItem() * impact.automationShare() / 60.0;
        double grossSaving = hoursSaved * impact.hourlyCostInr();
        double runCost = impact.runCostPerMonthInr() == null ? 0 : Math.max(0, impact.runCostPerMonthInr());
        double netSaving = grossSaving - runCost;

        double oneOffDays = impact.oneOffEffortDays() == null ? 0 : Math.max(0, impact.oneOffEffortDays());
        double oneOffCost = oneOffDays * WORKING_HOURS_PER_DAY * impact.hourlyCostInr();

        Double payback = null;
        if (netSaving > 0 && oneOffCost > 0) {
            double months = oneOffCost / netSaving;
            payback = months > MAX_MEANINGFUL_PAYBACK_MONTHS ? null : round(months);
        } else if (netSaving > 0) {
            // No build cost recorded, so it pays back the moment it runs. Recorded as zero rather
            // than left null, which would read as "unknown".
            payback = 0.0;
        }

        return new Computed(round(hoursSaved), round(grossSaving), round(runCost), round(netSaving),
                round(oneOffCost), payback);
    }

    /** Indian numbering, because the people checking these figures think in lakhs and crores. */
    public static String formatInr(double amount) {
        double absolute = Math.abs(amount);
        if (absolute >= 10_000_000) {
            return "%s%.2f crore".formatted(amount < 0 ? "-Rs " : "Rs ", absolute / 10_000_000);
        }
        if (absolute >= 100_000) {
            return "%s%.2f lakh".formatted(amount < 0 ? "-Rs " : "Rs ", absolute / 100_000);
        }
        return "%s%,.0f".formatted(amount < 0 ? "-Rs " : "Rs ", absolute);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
