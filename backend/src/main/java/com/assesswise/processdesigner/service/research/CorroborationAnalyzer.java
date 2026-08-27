package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.domain.ClaimRelation;
import com.assesswise.processdesigner.domain.ClaimRelationType;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.service.TextSimilarity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cross-checks the run's claims against each other.
 *
 * <p>A verified quote proves that a source said something. It does not prove the something is true.
 * The cheapest available check on that is whether anyone else, independently, said the same — and
 * whether anyone said the opposite. Both are computed here, and both are shown.
 *
 * <p>Two rules do most of the work:
 *
 * <ul>
 *   <li><b>Only different domains count.</b> Two pages on one publisher's site agreeing is one
 *       source with two URLs. Same-domain pairs are still recorded, marked {@code sameDomain}, but
 *       they do not raise a confidence score. Without this rule a run that happened to fetch four
 *       pages from one vendor would look extremely well corroborated.
 *   <li><b>Numbers disagreeing is the strongest contradiction signal available.</b> When two claims
 *       about the same topic both carry a figure in the same unit and those figures are far apart,
 *       that is a real disagreement rather than a wording difference — and it is exactly the case a
 *       reader most needs flagged, because a redesign built on the higher number is a different plan
 *       from one built on the lower.
 * </ul>
 *
 * <p>Contradictions are never resolved. Picking a winner would mean asserting something neither
 * source supports; the honest output is that credible sources disagree, which the interface shows
 * next to both claims.
 */
@Service
public class CorroborationAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CorroborationAnalyzer.class);

    /**
     * Below this, two claims are about different things and their relation means nothing.
     *
     * <p>Lowered from 0.55 after a live run cross-checked 27 real claims and found zero agreements.
     * Two sources stating the same fact in their own words share fewer stemmed terms than intuition
     * suggests — "e-marking cut turnaround by a third" and "digital scoring reduced result release
     * time by 30%" overlap on almost nothing.
     */
    private static final double SAME_TOPIC_THRESHOLD = 0.45;

    /**
     * A weaker overlap still counts when both claims carry a comparable figure. Two numbers in the
     * same unit about a similar topic are far more likely to be about the same thing than two
     * paragraphs of prose with the same term overlap, and this is where corroboration and
     * contradiction are both most valuable.
     */
    private static final double NUMERIC_TOPIC_THRESHOLD = 0.3;
    /** Figures further apart than this, relative to the larger, are treated as disagreeing. */
    private static final double NUMERIC_DIVERGENCE = 0.30;

    /** Words that flip a claim's polarity, so "AI matches human graders" and its denial differ. */
    private static final Set<String> NEGATION_CUES = Set.of(
            "not", "no", "never", "cannot", "fails", "failed", "unable", "insufficient",
            "unreliable", "inaccurate", "worse", "lower", "declined", "rejected", "banned",
            "prohibited", "withdrawn", "false", "overturned");

    /**
     * @param relations edges to persist
     * @param corroborations independent agreements found
     * @param contradictions disagreements found
     */
    public record Analysis(List<ClaimRelation> relations, int corroborations, int contradictions) {}

    /**
     * Compares every pair of claims, sets the counts and confidence on each claim, and returns the
     * relations worth storing.
     *
     * <p>Quadratic, and deliberately not optimised: the ceiling is around fifty claims a run, which
     * is 1,225 comparisons of two short strings. Anything cleverer would be harder to explain for
     * no measurable gain.
     */
    public Analysis analyse(List<EvidenceClaim> claims, Map<java.util.UUID, Integer> credibilityBySourceId) {
        List<ClaimRelation> relations = new ArrayList<>();
        Map<EvidenceClaim, Set<String>> agreeingDomains = new HashMap<>();
        Map<EvidenceClaim, Integer> contradictionCounts = new HashMap<>();

        for (int i = 0; i < claims.size(); i++) {
            for (int j = i + 1; j < claims.size(); j++) {
                EvidenceClaim left = claims.get(i);
                EvidenceClaim right = claims.get(j);

                double similarity = TextSimilarity.overlap(left.getClaimText(), right.getClaimText());
                boolean comparableNumbers = left.getNumericValue() != null
                        && right.getNumericValue() != null
                        && comparableUnits(left.getNumericUnit(), right.getNumericUnit());
                double threshold = comparableNumbers ? NUMERIC_TOPIC_THRESHOLD : SAME_TOPIC_THRESHOLD;

                // Topic labels are a second route to the same judgement: two claims the extractor
                // both tagged "grading turnaround" are about grading turnaround whatever words they
                // used to say it.
                boolean sameTopic = left.getTopic() != null && right.getTopic() != null
                        && TextSimilarity.overlap(left.getTopic(), right.getTopic()) >= 0.6;

                if (similarity < threshold && !sameTopic) {
                    continue;
                }
                String leftDomain = left.getSource().getDomain();
                String rightDomain = right.getSource().getDomain();
                boolean sameDomain = leftDomain != null && leftDomain.equalsIgnoreCase(rightDomain);

                Disagreement disagreement = disagreementBetween(left, right);
                ClaimRelationType type = disagreement.disagrees()
                        ? ClaimRelationType.CONTRADICTS
                        : ClaimRelationType.CORROBORATES;

                ClaimRelation relation = new ClaimRelation();
                relation.setClaimA(left);
                relation.setClaimB(right);
                relation.setRelationType(type);
                relation.setSimilarity(round(similarity));
                relation.setSameDomain(sameDomain);
                relation.setNote(disagreement.note() != null
                        ? disagreement.note()
                        : sameDomain
                                ? "Same publisher, so this is a repetition rather than independent support"
                                : "Independent agreement between %s and %s".formatted(leftDomain, rightDomain));
                relations.add(relation);

                if (type == ClaimRelationType.CONTRADICTS) {
                    contradictionCounts.merge(left, 1, Integer::sum);
                    contradictionCounts.merge(right, 1, Integer::sum);
                } else if (!sameDomain) {
                    agreeingDomains.computeIfAbsent(left, key -> new HashSet<>()).add(rightDomain);
                    agreeingDomains.computeIfAbsent(right, key -> new HashSet<>()).add(leftDomain);
                }
            }
        }

        for (EvidenceClaim claim : claims) {
            int corroborations = agreeingDomains.getOrDefault(claim, Set.of()).size();
            int contradictions = contradictionCounts.getOrDefault(claim, 0);
            claim.setCorroborationCount(corroborations);
            claim.setContradictionCount(contradictions);
            claim.setConfidence(confidenceFor(claim,
                    credibilityBySourceId.getOrDefault(claim.getSource().getId(), 40),
                    corroborations, contradictions));
        }

        int corroborations = (int) relations.stream()
                .filter(relation -> relation.getRelationType() == ClaimRelationType.CORROBORATES)
                .filter(relation -> !relation.isSameDomain())
                .count();
        int contradictions = (int) relations.stream()
                .filter(relation -> relation.getRelationType() == ClaimRelationType.CONTRADICTS)
                .count();

        log.info("Cross-checked {} claims: {} independent agreements, {} disagreements",
                claims.size(), corroborations, contradictions);
        return new Analysis(relations, corroborations, contradictions);
    }

    private record Disagreement(boolean disagrees, String note) {

        static Disagreement no() {
            return new Disagreement(false, null);
        }
    }

    private Disagreement disagreementBetween(EvidenceClaim left, EvidenceClaim right) {
        Double leftValue = left.getNumericValue();
        Double rightValue = right.getNumericValue();

        if (leftValue != null && rightValue != null && comparableUnits(left.getNumericUnit(), right.getNumericUnit())) {
            double larger = Math.max(Math.abs(leftValue), Math.abs(rightValue));
            if (larger > 0) {
                double divergence = Math.abs(leftValue - rightValue) / larger;
                if (divergence > NUMERIC_DIVERGENCE) {
                    return new Disagreement(true,
                            "The figures disagree: %s vs %s %s (%.0f%% apart). Both are shown; neither is chosen."
                                    .formatted(format(leftValue), format(rightValue),
                                            left.getNumericUnit() == null ? "" : left.getNumericUnit(),
                                            divergence * 100));
                }
                return Disagreement.no();
            }
        }

        // No figures to compare: fall back to polarity. Two claims about the same topic where one
        // is negated and the other is not are very likely opposed.
        boolean leftNegated = isNegated(left.getClaimText());
        boolean rightNegated = isNegated(right.getClaimText());
        if (leftNegated != rightNegated) {
            return new Disagreement(true,
                    "One of these claims asserts what the other denies about the same topic");
        }
        return Disagreement.no();
    }

    /** Units are compared loosely: "percent" and "%" are the same unit, "hours" and "days" are not. */
    private boolean comparableUnits(String left, String right) {
        String a = normaliseUnit(left);
        String b = normaliseUnit(right);
        if (a.isEmpty() || b.isEmpty()) {
            // A bare number with no unit on either side is not safe to compare — 40 what?
            return a.isEmpty() && b.isEmpty();
        }
        return a.equals(b);
    }

    private String normaliseUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return "";
        }
        String lower = unit.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "%", "percent", "percentage", "per cent", "pct" -> "percent";
            case "hour", "hours", "hrs", "hr" -> "hours";
            case "minute", "minutes", "mins", "min" -> "minutes";
            case "day", "days" -> "days";
            case "inr", "rupees", "rs", "rs.", "₹" -> "inr";
            case "usd", "dollars", "$" -> "usd";
            default -> lower;
        };
    }

    private boolean isNegated(String claimText) {
        if (claimText == null) {
            return false;
        }
        for (String token : TextSimilarity.normalize(claimText).split(" ")) {
            if (NEGATION_CUES.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * How much this particular claim is worth, 0..100.
     *
     * <p>Source credibility does most of the work, because it already folds in publication type,
     * recency and retrievability. On top of that: verification is a hard gate rather than a bonus —
     * an unverified quote loses most of its weight no matter how good the publisher is, since what
     * remains is a model's summary of a page nobody could check.
     */
    private double confidenceFor(EvidenceClaim claim, int sourceCredibility, int corroborations, int contradictions) {
        double base = sourceCredibility * 0.7;
        if (!claim.isQuoteVerified()) {
            base *= 0.45;
        }
        double typeWeight = switch (claim.getClaimType()) {
            case REGULATION, STATISTIC, BENCHMARK -> 8;
            case CAPABILITY, RISK, PRACTICE -> 5;
            case DEFINITION -> 3;
            case OPINION -> 0;
        };
        double corroborationBonus = Math.min(18, corroborations * 7);
        double contradictionPenalty = Math.min(20, contradictions * 8);

        double score = base + typeWeight + corroborationBonus - contradictionPenalty;
        return round(Math.max(0, Math.min(100, score)));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
