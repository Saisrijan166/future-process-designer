package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.RiskCategory;
import com.assesswise.processdesigner.service.NameMatcher;
import com.assesswise.processdesigner.service.NormalizedAnalysis;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiTask;
import com.assesswise.processdesigner.service.ai.StructuredJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Builds the risk register for the design that was just produced.
 *
 * <p>A redesign that hands part of an assessment decision to a model and does not enumerate what
 * could go wrong is a pitch, not a design. This stage reviews the future process as proposed and
 * asks the questions a compliance reviewer would: what happens when this is wrong about a candidate,
 * who owns the control, and what does the law here actually require.
 *
 * <p>The rule about obligations is the one that matters most. Models will happily assert that a
 * named statute requires something specific, and be wrong. So the prompt permits an obligation only
 * where the research established one, and this stage checks the claim: a risk claiming a legal
 * obligation while citing no evidence keeps its description but has the obligation stripped, and the
 * removal is recorded. A fabricated legal requirement in a compliance register is worse than a
 * missing one.
 */
@Component
@Order(80)
public class RiskStage extends ModelStage {

    private static final int MAX_TITLE = 250;
    private static final int MAX_TEXT = 8000;
    private static final int MAX_OBLIGATION = 400;
    private static final int MAX_ROLE = 150;
    private static final double MATCH_THRESHOLD = 0.45;

    /** Categories no AI redesign of work involving people should be missing. */
    private static final Set<RiskCategory> EXPECTED =
            EnumSet.of(RiskCategory.PRIVACY, RiskCategory.BIAS, RiskCategory.ACCURACY);

    public RiskStage(AiGateway gateway, StructuredJson json, PromptContextBuilder contexts) {
        super(gateway, json, contexts, AiTask.RISK, "prompts/assess-risks.txt");
    }

    @Override
    public String id() {
        return "risks";
    }

    @Override
    public String title() {
        return "Assess risks and obligations";
    }

    @Override
    protected String systemPrompt() {
        return "You are a risk and compliance reviewer for AI deployments that affect people. Your "
                + "controls are auditable, and you never assert a legal requirement that your evidence "
                + "does not establish. You return only valid JSON.";
    }

    @Override
    protected Map<String, Object> promptContext(PipelineContext context) {
        Map<String, Object> values = new LinkedHashMap<>(contexts.base(context));
        values.put("future_activities", contexts.futureActivityRows(context));
        values.put("opportunities", contexts.opportunityRows(context));
        values.put("evidence", contexts.evidenceRows(context, 12,
                Set.of(com.assesswise.processdesigner.domain.ClaimType.REGULATION,
                        com.assesswise.processdesigner.domain.ClaimType.RISK)));
        return values;
    }

    @Override
    protected Mapping map(JsonNode payload, PipelineContext context) {
        List<String> notes = new ArrayList<>();
        Set<Integer> allowedCitations = context.claimsByCitationIndex().keySet();
        List<NormalizedAnalysis.Risk> risks = new ArrayList<>();
        Set<RiskCategory> covered = EnumSet.noneOf(RiskCategory.class);

        for (JsonNode node : json.arrayAt(payload, "risks")) {
            String title = StageParsing.text(node, "title", MAX_TITLE);
            String description = StageParsing.text(node, "description", MAX_TEXT);
            if (title == null || description == null) {
                notes.add("Dropped a risk with no title or description.");
                continue;
            }
            RiskCategory category = StageParsing.riskCategory(node.path("category").asText(null));
            List<Integer> cited = StageParsing.citationIndices(
                    node, "cited_evidence", allowedCitations, notes, title);

            String obligation = StageParsing.text(node, "obligation", MAX_OBLIGATION);
            if (obligation != null && cited.isEmpty()) {
                notes.add(("Removed the stated obligation from \"%s\": it cited no evidence, and an "
                                + "unsupported legal requirement must not appear in a compliance register.")
                        .formatted(StageParsing.shorten(title)));
                obligation = null;
            }

            String mitigation = StageParsing.text(node, "mitigation", MAX_TEXT);
            if (mitigation == null) {
                notes.add("Risk \"%s\" has no mitigation, so it is recorded as unmanaged."
                        .formatted(StageParsing.shorten(title)));
            }

            covered.add(category);
            risks.add(new NormalizedAnalysis.Risk(
                    title,
                    description,
                    category,
                    StageParsing.bounded(node, "likelihood", 1, 5, 3),
                    StageParsing.bounded(node, "impact", 1, 5, 3),
                    mitigation,
                    StageParsing.text(node, "owner_role", MAX_ROLE),
                    obligation,
                    resolveOpportunity(node, context),
                    cited));
        }

        if (risks.isEmpty()) {
            return Mapping.unusable("\"risks\" was missing or contained no entry with a title and a "
                    + "description.");
        }

        Set<RiskCategory> missing = EnumSet.copyOf(EXPECTED);
        missing.removeAll(covered);
        if (!missing.isEmpty()) {
            // Stated rather than fabricated. Inventing the missing entry would be inventing a risk
            // assessment; naming the gap lets a human decide whether it is a real omission.
            notes.add(("The register has no entry for: %s. For a process handling personal data and "
                            + "influencing decisions about people, those categories are usually relevant.")
                    .formatted(missing));
        }

        context.setAnalysis(context.analysis().withRisks(risks));
        context.addWarnings(notes);

        long withObligation = risks.stream()
                .filter(risk -> risk.obligation() != null && !risk.obligation().isBlank()).count();
        long severe = risks.stream().filter(risk -> risk.likelihood() * risk.impact() >= 12).count();
        long withControl = risks.stream()
                .filter(risk -> risk.mitigation() != null && !risk.mitigation().isBlank()).count();

        String summary = "%d risks across %d categories, %d severe, %d with an auditable control, "
                + "%d tied to a cited obligation";
        return Mapping.of(summary.formatted(risks.size(), covered.size(), severe, withControl, withObligation),
                notes);
    }

    private String resolveOpportunity(JsonNode node, PipelineContext context) {
        String cited = StageParsing.text(node, "opportunity", MAX_TEXT);
        if (cited == null) {
            return null;
        }
        return NameMatcher.resolve(cited, context.analysis().opportunities(),
                        NormalizedAnalysis.Opportunity::description, MATCH_THRESHOLD)
                .map(NormalizedAnalysis.Opportunity::description)
                .orElse(null);
    }
}
