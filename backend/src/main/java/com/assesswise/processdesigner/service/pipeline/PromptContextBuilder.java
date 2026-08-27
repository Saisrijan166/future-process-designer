package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.domain.Problem;
import com.assesswise.processdesigner.domain.Role;
import com.assesswise.processdesigner.domain.SystemTool;
import com.assesswise.processdesigner.service.KnowledgeRetrievalService.ScoredSnippet;
import com.assesswise.processdesigner.service.NormalizedAnalysis;
import com.assesswise.processdesigner.domain.ClaimType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns the context into the rows the prompt templates iterate over.
 *
 * <p>Shared across the stages so that a given piece of information is described identically
 * wherever it appears. That consistency is worth a class of its own: the opportunity stage and the
 * risk stage both cite evidence, and if each rendered the evidence list its own way, the citation
 * numbers they return would mean different things.
 *
 * <p>Length limits throughout are about the 8,000 tokens-a-minute budget, not tidiness, and they
 * were tightened after a measured run: with longer quotes only twelve claims fitted and just two of
 * five recommendations ended up citing anything. Shorter quotes mean more claims in front of the
 * model, and more claims cited is the whole point of having gathered them.
 */
@Component
public class PromptContextBuilder {

    private static final int MAX_QUOTE_CHARS = 210;
    private static final int MAX_CLAIM_CHARS = 200;
    private static final int MAX_DESCRIPTION_CHARS = 600;

    /** Process identity plus today's date, which every stage needs and none should invent. */
    public Map<String, Object> base(PipelineContext context) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", context.process().getName());
        map.put("industry", context.process().getIndustry());
        map.put("description", context.process().getDescription());
        map.put("today", LocalDate.now());
        map.put("activity_count", context.activities().size());
        return map;
    }

    public List<Map<String, Object>> activityRows(PipelineContext context) {
        List<Map<String, Object>> rows = new ArrayList<>(context.activities().size());
        for (Activity activity : context.activities()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sequence_order", activity.getSequenceOrder());
            row.put("name", activity.getName());
            row.put("description", blankToPhrase(activity.getDescription(), "no description provided"));
            row.put("roles", joinOrNone(activity.getRoles().stream().map(Role::getName).toList()));
            row.put("systems", joinOrNone(activity.getSystems().stream().map(SystemTool::getName).toList()));
            rows.add(row);
        }
        return rows;
    }

    /** Problems recorded on the process before any analysis ran. */
    public List<Map<String, Object>> knownProblemRows(PipelineContext context) {
        List<Map<String, Object>> rows = new ArrayList<>(context.knownProblems().size());
        for (Problem problem : context.knownProblems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("severity", problem.getSeverity());
            row.put("description", problem.getDescription());
            rows.add(row);
        }
        return rows;
    }

    /** Problems the diagnosis stage produced, for the stages that build on them. */
    public List<Map<String, Object>> diagnosedProblemRows(PipelineContext context) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NormalizedAnalysis.Problem problem : context.analysis().problems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("severity", problem.severity());
            row.put("activity_name", blankToPhrase(problem.activityName(), "whole process"));
            row.put("description", problem.description());
            row.put("root_cause", blankToPhrase(problem.rootCause(), "not established"));
            rows.add(row);
        }
        return rows;
    }

    /**
     * The evidence, numbered exactly as the model must cite it.
     *
     * <p>Unverified claims are included and labelled. Hiding them would make the model's view of the
     * research rosier than the truth, and the label is doing real work: the prompts tell the model
     * that citing an unverified claim is allowed but that it must not present it as established.
     */
    public List<Map<String, Object>> evidenceRows(PipelineContext context) {
        return evidenceRows(context, Integer.MAX_VALUE, Set.of());
    }

    /**
     * The evidence, capped and optionally biased towards particular kinds of claim.
     *
     * <p>The cap is a budget decision. Twelve claims with their quotes is about 1,500 tokens of an
     * 8,000 tokens-per-minute allowance, and a stage that does not reason about quotes — the
     * roadmap, for instance — should not be paying for them. Preferred types come first rather than
     * exclusively: the quantification stage wants measured figures, but a capability claim that
     * bounds what is achievable is still worth its place.
     */
    public List<Map<String, Object>> evidenceRows(
            PipelineContext context, int limit, Set<ClaimType> preferredTypes) {

        List<EvidenceClaim> claims = new ArrayList<>(context.allClaims());
        if (!preferredTypes.isEmpty()) {
            claims.sort(Comparator
                    .comparing((EvidenceClaim claim) -> preferredTypes.contains(claim.getClaimType()) ? 0 : 1)
                    .thenComparing(Comparator.comparingInt(EvidenceClaim::getCitationIndex)));
        }
        if (claims.size() > limit) {
            claims = claims.subList(0, limit);
            // Restore citation order, so the numbers in the prompt still read in sequence.
            claims = new ArrayList<>(claims);
            claims.sort(Comparator.comparingInt(EvidenceClaim::getCitationIndex));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (EvidenceClaim claim : claims) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", claim.getCitationIndex());
            row.put("claim_type", claim.getClaimType());
            row.put("claim", truncate(claim.getClaimText(), MAX_CLAIM_CHARS));
            row.put("quote", truncate(claim.getQuote(), MAX_QUOTE_CHARS));
            row.put("source", claim.getSource().getDomain());
            row.put("source_type", claim.getSource().getSourceType());
            row.put("published", claim.getSource().getPublishedAt() == null
                    ? "date unknown"
                    : claim.getSource().getPublishedAt().toString());
            row.put("credibility", claim.getSource().getCredibilityScore());
            row.put("verified", claim.isQuoteVerified()
                    ? "VERIFIED"
                    : "UNVERIFIED — the quote could not be found in the page, treat with caution");
            row.put("corroboration", claim.getCorroborationCount() == 0
                    ? "no independent confirmation"
                    : claim.getCorroborationCount() + " independent domain(s) agree");
            row.put("contradiction", claim.getContradictionCount() == 0
                    ? ""
                    : " CONTRADICTED by " + claim.getContradictionCount() + " other claim(s)");
            rows.add(row);
        }
        return rows;
    }

    /** The curated corpus, used when live research produced nothing. */
    public List<Map<String, Object>> curatedSnippetRows(PipelineContext context) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScoredSnippet scored : context.curatedSnippets()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source_type", scored.snippet().getSourceType());
            row.put("title", scored.snippet().getTitle());
            row.put("snippet_text", scored.snippet().getSnippetText());
            row.put("source_url", scored.snippet().getSourceUrl());
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, Object>> opportunityRows(PipelineContext context) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 1;
        for (NormalizedAnalysis.Opportunity opportunity : context.analysis().opportunities()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", index++);
            row.put("activity_name", blankToPhrase(opportunity.activityName(), "whole process"));
            row.put("description", truncate(opportunity.description(), MAX_DESCRIPTION_CHARS));
            row.put("ai_capability", opportunity.aiCapability());
            row.put("automation_potential", opportunity.automationPotential());
            row.put("business_benefit", blankToPhrase(opportunity.businessBenefit(), "not stated"));
            row.put("risk", blankToPhrase(opportunity.risk(), "not stated"));
            row.put("human_oversight", blankToPhrase(opportunity.humanOversight(), "not stated"));
            row.put("cited_evidence", opportunity.citedEvidence().isEmpty()
                    ? "none"
                    : opportunity.citedEvidence().stream().map(value -> "[" + value + "]").toList().toString());
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, Object>> futureActivityRows(PipelineContext context) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NormalizedAnalysis.FutureStep step : context.analysis().futureActivities()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sequence_order", step.sequenceOrder());
            row.put("name", step.name());
            row.put("responsibility_type", step.responsibilityType());
            row.put("description", truncate(step.description(), MAX_DESCRIPTION_CHARS));
            row.put("human_responsibility", blankToPhrase(step.humanResponsibility(), "not stated"));
            row.put("ai_responsibility", blankToPhrase(step.aiResponsibility(), "none"));
            rows.add(row);
        }
        return rows;
    }

    /** One line summarising what the research found, for stages that do not list every claim. */
    public String researchSummary(PipelineContext context) {
        if (context.research() == null) {
            return "No live research was run for this analysis.";
        }
        return context.research().summary() == null
                ? "Live research status: " + context.research().status()
                : context.research().summary();
    }

    private String joinOrNone(List<String> values) {
        return values.isEmpty() ? "not recorded" : String.join(", ", values);
    }

    private String blankToPhrase(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max) + "...";
    }
}
