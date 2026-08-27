package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.domain.OpportunityVerdict;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.service.NormalizedAnalysis;
import com.assesswise.processdesigner.service.TextSimilarity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Measures how good a run actually was, from the run's own output.
 *
 * <p>Every AI application will tell you its answer is good. This one computes six numbers from
 * stored rows and shows the arithmetic, including when the arithmetic is unflattering — a run whose
 * sources were all blocked <em>should</em> score badly on grounding, and a system that cannot report
 * that about itself is asking to be trusted rather than inspected.
 *
 * <p>Nothing here asks a model anything. Each component is a ratio over data the pipeline produced:
 *
 * <ul>
 *   <li><b>Coverage</b> — of the activities in the current process, how many does the analysis
 *       actually touch? An analysis that redesigns two steps of a nine-step process has covered two
 *       steps, however good those two are.
 *   <li><b>Grounding</b> — what share of opportunities cite at least one quote-verified claim.
 *   <li><b>Corroboration</b> — what share of the verified evidence a second, independent domain
 *       agreed with.
 *   <li><b>Agreement</b> — how the adversarial reviewer scored the proposals. A low number here is
 *       not a bug in the scorecard; it is the reviewer doing its job.
 *   <li><b>Specificity</b> — whether the output names capabilities, metrics, data and failure modes,
 *       or whether it waffles. Measured by counting filled fields that the prompts require.
 *   <li><b>Traceability</b> — what share of generated rows resolve back to something stored: an
 *       activity, an opportunity, a claim.
 * </ul>
 *
 * <p>The weights favour grounding and traceability over volume, because the point of the application
 * is that its output can be checked rather than that there is a lot of it.
 */
@Component
public class ScorecardCalculator {

    private static final Logger log = LoggerFactory.getLogger(ScorecardCalculator.class);

    private final ObjectMapper objectMapper;

    public ScorecardCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record Scorecard(
            int coverage,
            int grounding,
            int corroboration,
            int agreement,
            int specificity,
            int traceability,
            int overall,
            String grade,
            String metricsJson,
            String summary) {}

    public Scorecard compute(PipelineContext context) {
        NormalizedAnalysis analysis = context.analysis();
        Map<String, Object> metrics = new LinkedHashMap<>();

        int coverage = coverage(context, analysis, metrics);
        int grounding = grounding(context, analysis, metrics);
        int corroboration = corroboration(context, metrics);
        int agreement = agreement(analysis, metrics);
        int specificity = specificity(analysis, metrics);
        int traceability = traceability(analysis, metrics);

        // Grounding and traceability are weighted highest because they are what distinguishes this
        // from a well-written guess.
        double weighted = coverage * 0.15
                + grounding * 0.25
                + corroboration * 0.10
                + agreement * 0.15
                + specificity * 0.15
                + traceability * 0.20;
        int overall = (int) Math.round(weighted);

        String grade = gradeFor(overall);
        String summary = "%d/100 (%s): coverage %d, grounding %d, corroboration %d, reviewer agreement %d, "
                + "specificity %d, traceability %d";

        return new Scorecard(coverage, grounding, corroboration, agreement, specificity, traceability,
                overall, grade, toJson(metrics),
                summary.formatted(overall, grade, coverage, grounding, corroboration, agreement,
                        specificity, traceability));
    }

    /** How much of the current process the analysis actually engages with. */
    private int coverage(PipelineContext context, NormalizedAnalysis analysis, Map<String, Object> metrics) {
        List<Activity> activities = context.activities();
        if (activities.isEmpty()) {
            // Nothing to cover. Neutral rather than perfect: a process with no activities recorded
            // has not been comprehensively analysed, it has been analysed from a paragraph.
            metrics.put("coverage_note", "no activities recorded, so coverage cannot be measured");
            return 50;
        }
        int addressed = 0;
        for (Activity activity : activities) {
            if (mentions(analysis, activity.getName())) {
                addressed++;
            }
        }
        metrics.put("activities_total", activities.size());
        metrics.put("activities_addressed", addressed);
        return percentage(addressed, activities.size());
    }

    private boolean mentions(NormalizedAnalysis analysis, String activityName) {
        return analysis.opportunities().stream().anyMatch(item -> matches(item.activityName(), activityName))
                || analysis.problems().stream().anyMatch(item -> matches(item.activityName(), activityName))
                || analysis.futureActivities().stream()
                        .anyMatch(step -> matches(step.replacesActivity(), activityName));
    }

    private boolean matches(String candidate, String activityName) {
        return candidate != null && !candidate.isBlank()
                && TextSimilarity.overlap(candidate, activityName) >= 0.6;
    }

    /** What share of the recommendations rest on a quote somebody could check. */
    private int grounding(PipelineContext context, NormalizedAnalysis analysis, Map<String, Object> metrics) {
        List<NormalizedAnalysis.Opportunity> opportunities = analysis.opportunities();
        if (opportunities.isEmpty()) {
            return 0;
        }
        Map<Integer, EvidenceClaim> claims = context.claimsByCitationIndex();
        long grounded = opportunities.stream()
                .filter(opportunity -> opportunity.citedEvidence().stream()
                        .map(claims::get)
                        .anyMatch(claim -> claim != null && claim.isQuoteVerified()))
                .count();

        metrics.put("opportunities_total", opportunities.size());
        metrics.put("opportunities_grounded", grounded);
        metrics.put("verified_claims_available", context.citableClaims().size());

        if (context.citableClaims().isEmpty()) {
            metrics.put("grounding_note",
                    "no verified evidence was available to cite, so nothing could be grounded");
            return 0;
        }
        return percentage(grounded, opportunities.size());
    }

    /** How much of the evidence a second independent publisher agreed with. */
    private int corroboration(PipelineContext context, Map<String, Object> metrics) {
        List<EvidenceClaim> verified = context.citableClaims();
        if (verified.isEmpty()) {
            metrics.put("corroboration_note", "no verified claims to cross-check");
            return 0;
        }
        long corroborated = verified.stream().filter(claim -> claim.getCorroborationCount() > 0).count();
        long contradicted = verified.stream().filter(claim -> claim.getContradictionCount() > 0).count();

        metrics.put("verified_claims", verified.size());
        metrics.put("claims_corroborated", corroborated);
        metrics.put("claims_contradicted", contradicted);

        int base = percentage(corroborated, verified.size());
        // Contradictions are not penalised heavily: finding them is a success of the method, and the
        // interface shows both sides. They do reduce how settled the evidence is.
        int penalty = (int) Math.min(15, contradicted * 5);
        return Math.max(0, base - penalty);
    }

    /** What the adversarial reviewer thought. A low score here means the review found problems. */
    private int agreement(NormalizedAnalysis analysis, Map<String, Object> metrics) {
        List<NormalizedAnalysis.Critique> critiques = analysis.critiques();
        if (critiques.isEmpty()) {
            metrics.put("agreement_note", "the review stage did not run, so nothing was independently checked");
            return 0;
        }
        double total = 0;
        for (NormalizedAnalysis.Critique critique : critiques) {
            total += switch (critique.verdict()) {
                case STRONG -> 100;
                case SOUND -> 80;
                case QUALIFIED -> 55;
                case WEAK -> 25;
                case REJECTED -> 0;
            };
        }
        metrics.put("reviews", critiques.size());
        metrics.put("verdict_strong", count(critiques, OpportunityVerdict.STRONG));
        metrics.put("verdict_sound", count(critiques, OpportunityVerdict.SOUND));
        metrics.put("verdict_qualified", count(critiques, OpportunityVerdict.QUALIFIED));
        metrics.put("verdict_weak", count(critiques, OpportunityVerdict.WEAK));
        metrics.put("verdict_rejected", count(critiques, OpportunityVerdict.REJECTED));
        return (int) Math.round(total / critiques.size());
    }

    /** Whether the output is specific enough to act on, measured by the fields the prompts require. */
    private int specificity(NormalizedAnalysis analysis, Map<String, Object> metrics) {
        int checks = 0;
        int filled = 0;

        for (NormalizedAnalysis.Opportunity opportunity : analysis.opportunities()) {
            checks += 4;
            filled += present(opportunity.humanOversight());
            filled += present(opportunity.dataRequirement());
            filled += present(opportunity.successMetric());
            filled += opportunity.aiCapability() != null
                    && !opportunity.aiCapability().startsWith("Unspecified") ? 1 : 0;
        }
        for (NormalizedAnalysis.FutureStep step : analysis.futureActivities()) {
            checks += 2;
            filled += present(step.humanResponsibility());
            // A human-led step needs no AI failure mode, so it is not counted against the design.
            filled += step.responsibilityType() == ResponsibilityType.HUMAN_LED ? 1 : present(step.failureMode());
        }
        for (NormalizedAnalysis.Risk risk : analysis.risks()) {
            checks += 2;
            filled += present(risk.mitigation());
            filled += present(risk.ownerRole());
        }
        for (NormalizedAnalysis.Problem problem : analysis.problems()) {
            checks += 1;
            filled += present(problem.rootCause());
        }

        metrics.put("specificity_fields_expected", checks);
        metrics.put("specificity_fields_present", filled);
        return checks == 0 ? 0 : percentage(filled, checks);
    }

    /** What share of generated rows point back at something stored. */
    private int traceability(NormalizedAnalysis analysis, Map<String, Object> metrics) {
        int links = 0;
        int possible = 0;

        possible += analysis.interventions().size();
        links += (int) analysis.interventions().stream()
                .filter(intervention -> present(intervention.relatedOpportunityDescription()) == 1)
                .count();

        possible += analysis.futureActivities().size();
        links += (int) analysis.futureActivities().stream()
                .filter(step -> present(step.replacesActivity()) == 1)
                .count();

        possible += analysis.risks().size();
        links += (int) analysis.risks().stream()
                .filter(risk -> !risk.citedEvidence().isEmpty() || present(risk.opportunityDescription()) == 1)
                .count();

        possible += analysis.roadmap().size();
        links += (int) analysis.roadmap().stream()
                .filter(item -> present(item.opportunityDescription()) == 1 || present(item.dependsOn()) == 1)
                .count();

        possible += analysis.impacts().size();
        links += (int) analysis.impacts().stream()
                .filter(impact -> present(impact.opportunityDescription()) == 1)
                .count();

        metrics.put("traceable_rows", links);
        metrics.put("traceable_rows_possible", possible);
        return possible == 0 ? 0 : percentage(links, possible);
    }

    private long count(List<NormalizedAnalysis.Critique> critiques, OpportunityVerdict verdict) {
        return critiques.stream().filter(critique -> critique.verdict() == verdict).count();
    }

    private int present(String value) {
        return value != null && !value.isBlank() ? 1 : 0;
    }

    private int percentage(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * part / total);
    }

    private String gradeFor(int overall) {
        if (overall >= 85) {
            return "A";
        }
        if (overall >= 70) {
            return "B";
        }
        if (overall >= 55) {
            return "C";
        }
        return overall >= 40 ? "D" : "E";
    }

    private String toJson(Map<String, Object> metrics) {
        try {
            ObjectNode root = objectMapper.valueToTree(metrics);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.debug("Could not serialise scorecard metrics: {}", e.getMessage());
            return null;
        }
    }
}
