package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.domain.Problem;
import com.assesswise.processdesigner.service.KnowledgeRetrievalService.ScoredSnippet;
import com.assesswise.processdesigner.service.NormalizedAnalysis;
import com.assesswise.processdesigner.service.progress.ProgressSink;
import com.assesswise.processdesigner.service.research.ResearchOrchestrator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the stages share.
 *
 * <p>A plain mutable object rather than a chain of immutable results, because that is what the work
 * actually is: ten stages that each read everything gathered so far and add one more part. Threading
 * an eight-argument record through ten stages would be ceremony, and the alternative — each stage
 * re-reading the database — would mean the pipeline could not be reasoned about without also
 * reasoning about transaction boundaries.
 *
 * <p>Single-threaded by design. The stages run in order because each genuinely depends on the last;
 * the concurrency in this application is inside the research stage, where it belongs.
 */
public class PipelineContext {

    private final UUID processId;
    private final UUID analysisRunId;
    private final BusinessProcess process;
    private final List<Activity> activities;
    private final List<Problem> knownProblems;
    private final List<ScoredSnippet> curatedSnippets;
    private final ProgressSink sink;

    /** Built up stage by stage; handed to persistence once at the end. */
    private NormalizedAnalysis analysis = new NormalizedAnalysis(List.of(), List.of(), List.of(), List.of());

    private ResearchOrchestrator.ResearchOutcome research;

    /**
     * Evidence by the citation number the model was shown. This map is the contract between the
     * research stage and every stage that cites: a model writes {@code [3]}, and this is what
     * {@code 3} means. An index not in here was never shown and is discarded rather than stored.
     */
    private final Map<Integer, EvidenceClaim> claimsByCitationIndex = new LinkedHashMap<>();

    private final List<String> warnings = new ArrayList<>();

    /** Set by the final stage, which measures the run from everything above. */
    private ScorecardCalculator.Scorecard scorecard;

    public PipelineContext(
            UUID processId,
            UUID analysisRunId,
            BusinessProcess process,
            List<Activity> activities,
            List<Problem> knownProblems,
            List<ScoredSnippet> curatedSnippets,
            ProgressSink sink) {
        this.processId = processId;
        this.analysisRunId = analysisRunId;
        this.process = process;
        this.activities = List.copyOf(activities);
        this.knownProblems = List.copyOf(knownProblems);
        this.curatedSnippets = List.copyOf(curatedSnippets);
        this.sink = sink == null ? ProgressSink.NONE : sink;
    }

    public UUID processId() {
        return processId;
    }

    public UUID analysisRunId() {
        return analysisRunId;
    }

    public BusinessProcess process() {
        return process;
    }

    public List<Activity> activities() {
        return activities;
    }

    public List<Problem> knownProblems() {
        return knownProblems;
    }

    /** The hand-curated corpus, still used to ground a run whose live research came back empty. */
    public List<ScoredSnippet> curatedSnippets() {
        return curatedSnippets;
    }

    public ProgressSink sink() {
        return sink;
    }

    public NormalizedAnalysis analysis() {
        return analysis;
    }

    public void setAnalysis(NormalizedAnalysis value) {
        this.analysis = value;
    }

    public ResearchOrchestrator.ResearchOutcome research() {
        return research;
    }

    public void setResearch(ResearchOrchestrator.ResearchOutcome value) {
        this.research = value;
        claimsByCitationIndex.clear();
        if (value != null) {
            value.claims().forEach(claim -> claimsByCitationIndex.put(claim.getCitationIndex(), claim));
        }
    }

    public Map<Integer, EvidenceClaim> claimsByCitationIndex() {
        return Map.copyOf(claimsByCitationIndex);
    }

    /** Only verified claims, in citation order — what the citing stages are actually shown. */
    public List<EvidenceClaim> citableClaims() {
        return claimsByCitationIndex.values().stream()
                .filter(EvidenceClaim::isQuoteVerified)
                .toList();
    }

    /**
     * Every claim, verified or not, in citation order.
     *
     * <p>Unverified claims are shown to the model too, clearly labelled, because a source that could
     * not be read is still a lead worth reasoning about. What they cannot do is raise a grounding
     * score, which is enforced when the citations are persisted rather than left to the model.
     */
    public List<EvidenceClaim> allClaims() {
        return List.copyOf(claimsByCitationIndex.values());
    }

    public boolean hasLiveEvidence() {
        return !claimsByCitationIndex.isEmpty();
    }

    public ScorecardCalculator.Scorecard scorecard() {
        return scorecard;
    }

    public void setScorecard(ScorecardCalculator.Scorecard value) {
        this.scorecard = value;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            warnings.add(warning);
        }
    }

    public void addWarnings(List<String> values) {
        if (values != null) {
            values.forEach(this::addWarning);
        }
    }
}
