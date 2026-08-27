package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.AiIntervention;
import com.assesswise.processdesigner.domain.AiOpportunity;
import com.assesswise.processdesigner.domain.AnalysisRun;
import com.assesswise.processdesigner.domain.AnalysisScorecard;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.domain.FutureActivity;
import com.assesswise.processdesigner.domain.ImpactEstimate;
import com.assesswise.processdesigner.domain.KnowledgeSnippet;
import com.assesswise.processdesigner.domain.OpportunityScore;
import com.assesswise.processdesigner.domain.ProblemSource;
import com.assesswise.processdesigner.domain.ProcessStatus;
import com.assesswise.processdesigner.domain.RiskItem;
import com.assesswise.processdesigner.domain.RoadmapItem;
import com.assesswise.processdesigner.exception.ResourceNotFoundException;
import com.assesswise.processdesigner.repository.ActivityRepository;
import com.assesswise.processdesigner.repository.AiInterventionRepository;
import com.assesswise.processdesigner.repository.AiOpportunityRepository;
import com.assesswise.processdesigner.repository.AnalysisRunRepository;
import com.assesswise.processdesigner.repository.AnalysisScorecardRepository;
import com.assesswise.processdesigner.repository.BusinessProcessRepository;
import com.assesswise.processdesigner.repository.EvidenceClaimRepository;
import com.assesswise.processdesigner.repository.FutureActivityRepository;
import com.assesswise.processdesigner.repository.ImpactEstimateRepository;
import com.assesswise.processdesigner.repository.KnowledgeSnippetRepository;
import com.assesswise.processdesigner.repository.OpportunityScoreRepository;
import com.assesswise.processdesigner.repository.ProblemRepository;
import com.assesswise.processdesigner.repository.RiskItemRepository;
import com.assesswise.processdesigner.repository.RoadmapItemRepository;
import com.assesswise.processdesigner.service.KnowledgeRetrievalService.ScoredSnippet;
import com.assesswise.processdesigner.service.pipeline.ImpactCalculator;
import com.assesswise.processdesigner.service.pipeline.ScorecardCalculator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a validated analysis into rows, wiring every foreign key so the result is queryable
 * rather than a blob of text.
 *
 * <p>Deliberately separate from the pipeline: a run takes a minute or more of model calls and HTTP
 * requests and must not hold a database transaction open across any of it — especially against a
 * serverless Postgres with a small connection allowance. Everything below happens in one short
 * transaction at the end, so a re-analysis is atomic: either the new future state replaces the old
 * one completely, or nothing changes.
 *
 * <p>Two things here are worth reading closely, because they are where "traceable output" is either
 * true or merely claimed:
 *
 * <ul>
 *   <li><b>Citations are re-resolved, not trusted.</b> A model cites {@code [3]}; the claim that was
 *       shown as 3 is looked up and linked as a row. A number that does not resolve is dropped with
 *       a warning rather than stored, because a footnote pointing at nothing is worse than none.
 *   <li><b>Grounding is computed here, from verified claims only.</b> Not asserted by a stage, and
 *       not raised by citing a claim whose quote could not be found in its source.
 * </ul>
 */
@Service
public class AnalysisPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPersistenceService.class);

    private final BusinessProcessRepository processRepository;
    private final ActivityRepository activityRepository;
    private final ProblemRepository problemRepository;
    private final AiOpportunityRepository opportunityRepository;
    private final FutureActivityRepository futureActivityRepository;
    private final AiInterventionRepository interventionRepository;
    private final KnowledgeSnippetRepository snippetRepository;
    private final EvidenceClaimRepository claimRepository;
    private final OpportunityScoreRepository scoreRepository;
    private final ImpactEstimateRepository impactRepository;
    private final RiskItemRepository riskRepository;
    private final RoadmapItemRepository roadmapRepository;
    private final AnalysisScorecardRepository scorecardRepository;
    private final AnalysisRunRepository runRepository;
    private final ImpactCalculator impactCalculator;
    private final double nameMatchThreshold;
    private final boolean dropUngrounded;

    public AnalysisPersistenceService(
            BusinessProcessRepository processRepository,
            ActivityRepository activityRepository,
            ProblemRepository problemRepository,
            AiOpportunityRepository opportunityRepository,
            FutureActivityRepository futureActivityRepository,
            AiInterventionRepository interventionRepository,
            KnowledgeSnippetRepository snippetRepository,
            EvidenceClaimRepository claimRepository,
            OpportunityScoreRepository scoreRepository,
            ImpactEstimateRepository impactRepository,
            RiskItemRepository riskRepository,
            RoadmapItemRepository roadmapRepository,
            AnalysisScorecardRepository scorecardRepository,
            AnalysisRunRepository runRepository,
            ImpactCalculator impactCalculator,
            AppProperties properties) {
        this.processRepository = processRepository;
        this.activityRepository = activityRepository;
        this.problemRepository = problemRepository;
        this.opportunityRepository = opportunityRepository;
        this.futureActivityRepository = futureActivityRepository;
        this.interventionRepository = interventionRepository;
        this.snippetRepository = snippetRepository;
        this.claimRepository = claimRepository;
        this.scoreRepository = scoreRepository;
        this.impactRepository = impactRepository;
        this.riskRepository = riskRepository;
        this.roadmapRepository = roadmapRepository;
        this.scorecardRepository = scorecardRepository;
        this.runRepository = runRepository;
        this.impactCalculator = impactCalculator;
        this.nameMatchThreshold = properties.analysis().nameMatchThreshold();
        this.dropUngrounded = properties.analysis().dropUngroundedOpportunities();
    }

    public record PersistResult(
            int problems,
            int opportunities,
            int futureActivities,
            int interventions,
            int reviews,
            int impacts,
            int risks,
            int roadmapItems,
            int citations,
            List<String> warnings) {

        public PersistResult(
                int problems, int opportunities, int futureActivities, int interventions, List<String> warnings) {
            this(problems, opportunities, futureActivities, interventions, 0, 0, 0, 0, 0, warnings);
        }
    }

    /**
     * Everything one write needs.
     *
     * @param claimsByCitationIndex the numbers the model was shown, so its citations can be resolved
     *     back to rows. Empty for a run with no live research.
     * @param analysisRunId the run the scorecard belongs to; null for the legacy single-call path
     */
    public record PersistCommand(
            UUID processId,
            NormalizedAnalysis analysis,
            List<ScoredSnippet> retrievedSnippets,
            Map<Integer, UUID> claimsByCitationIndex,
            UUID analysisRunId,
            ScorecardCalculator.Scorecard scorecard) {

        public static PersistCommand legacy(
                UUID processId, NormalizedAnalysis analysis, List<ScoredSnippet> snippets) {
            return new PersistCommand(processId, analysis, snippets, Map.of(), null, null);
        }
    }

    /** The original entry point, still used by the single-call fallback pipeline. */
    @Transactional
    public PersistResult replaceAnalysis(
            UUID processId, NormalizedAnalysis analysis, List<ScoredSnippet> retrievedSnippets) {
        return replaceAnalysis(PersistCommand.legacy(processId, analysis, retrievedSnippets));
    }

    /**
     * Replaces the AI-generated state of a process. Re-running an analysis is therefore idempotent:
     * previous opportunities, future activities, interventions, reviews, estimates, risks and
     * roadmap items are removed first, so no duplicates or orphans accumulate.
     */
    @Transactional
    public PersistResult replaceAnalysis(PersistCommand command) {
        UUID processId = command.processId();
        NormalizedAnalysis analysis = command.analysis();
        List<String> warnings = new ArrayList<>();

        clearPreviousAnalysis(processId);

        BusinessProcess process = processRepository.findById(processId)
                .orElseThrow(() -> ResourceNotFoundException.of("Process", processId));
        List<Activity> activities = activityRepository.findByProcessIdOrderBySequenceOrderAsc(processId);

        // Re-read inside this transaction: the copies handed in were loaded during earlier stages
        // and are detached, and they are about to become join-table rows.
        List<KnowledgeSnippet> groundingSnippets = snippetRepository.findAllById(
                command.retrievedSnippets().stream().map(scored -> scored.snippet().getId()).toList());
        Map<Integer, EvidenceClaim> claims = loadClaims(command.claimsByCitationIndex());

        List<com.assesswise.processdesigner.domain.Problem> problems =
                persistProblems(process, activities, analysis, warnings);
        List<AiOpportunity> opportunities =
                persistOpportunities(process, activities, groundingSnippets, claims, analysis, warnings);
        List<FutureActivity> futureActivities = persistFutureActivities(process, analysis);
        List<AiIntervention> interventions =
                persistInterventions(process, futureActivities, opportunities, analysis, warnings);

        int reviews = persistReviews(opportunities, analysis, warnings);
        int impacts = persistImpacts(process, activities, opportunities, analysis, warnings);
        int risks = persistRisks(process, opportunities, claims, analysis, warnings);
        int roadmapItems = persistRoadmap(process, opportunities, analysis);
        persistScorecard(process, command, warnings);

        int citations = opportunities.stream().mapToInt(opportunity -> opportunity.getCitedClaims().size()).sum();

        process.setStatus(ProcessStatus.ANALYZED);
        process.setLastAnalyzedAt(Instant.now());
        processRepository.save(process);

        return new PersistResult(problems.size(), opportunities.size(), futureActivities.size(),
                interventions.size(), reviews, impacts, risks, roadmapItems, citations, warnings);
    }

    /**
     * Order matters and is enforced here rather than left to cascades: interventions reference both
     * future activities and opportunities, and estimates, reviews and risks all reference
     * opportunities.
     */
    private void clearPreviousAnalysis(UUID processId) {
        int removedInterventions = interventionRepository.deleteByProcessId(processId);
        int removedFuture = futureActivityRepository.deleteByProcessId(processId);
        impactRepository.deleteByProcessId(processId);
        riskRepository.deleteByProcessId(processId);
        roadmapRepository.deleteByProcessId(processId);
        // Reviews are keyed by opportunity and cascade with it; deleting opportunities is enough.
        int removedOpportunities = opportunityRepository.deleteByProcessId(processId);
        int removedProblems = problemRepository.deleteByProcessIdAndSource(processId, ProblemSource.AI_GENERATED);

        if (removedInterventions + removedFuture + removedOpportunities + removedProblems > 0) {
            log.info("Re-analysis of process {}: cleared {} problems, {} opportunities, {} future activities, "
                            + "{} interventions", processId, removedProblems, removedOpportunities, removedFuture,
                    removedInterventions);
        }
    }

    private Map<Integer, EvidenceClaim> loadClaims(Map<Integer, UUID> claimsByCitationIndex) {
        if (claimsByCitationIndex.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EvidenceClaim> byId = new LinkedHashMap<>();
        claimRepository.findAllById(claimsByCitationIndex.values())
                .forEach(claim -> byId.put(claim.getId(), claim));

        Map<Integer, EvidenceClaim> resolved = new LinkedHashMap<>();
        claimsByCitationIndex.forEach((index, id) -> {
            EvidenceClaim claim = byId.get(id);
            if (claim != null) {
                resolved.put(index, claim);
            }
        });
        return resolved;
    }

    private List<com.assesswise.processdesigner.domain.Problem> persistProblems(
            BusinessProcess process,
            List<Activity> activities,
            NormalizedAnalysis analysis,
            List<String> warnings) {

        List<com.assesswise.processdesigner.domain.Problem> entities = new ArrayList<>();
        for (NormalizedAnalysis.Problem item : analysis.problems()) {
            com.assesswise.processdesigner.domain.Problem entity =
                    new com.assesswise.processdesigner.domain.Problem();
            entity.setProcess(process);
            entity.setActivity(resolveActivity(item.activityName(), activities, "problem", warnings));
            entity.setDescription(item.description());
            entity.setSeverity(item.severity());
            entity.setSource(ProblemSource.AI_GENERATED);
            entity.setRootCause(item.rootCause());
            entity.setEvidenceNote(item.evidenceNote());
            entities.add(entity);
        }
        return problemRepository.saveAll(entities);
    }

    private List<AiOpportunity> persistOpportunities(
            BusinessProcess process,
            List<Activity> activities,
            List<KnowledgeSnippet> groundingSnippets,
            Map<Integer, EvidenceClaim> claims,
            NormalizedAnalysis analysis,
            List<String> warnings) {

        List<AiOpportunity> entities = new ArrayList<>();
        int order = 0;
        for (NormalizedAnalysis.Opportunity item : analysis.opportunities()) {
            List<EvidenceClaim> cited = resolveClaims(item.citedEvidence(), claims, item.description(), warnings);
            int groundingScore = groundingScoreFor(cited);

            if (groundingScore == 0 && dropUngrounded && !claims.isEmpty()) {
                warnings.add("Dropped ungrounded opportunity \"%s\" (configured to require evidence)."
                        .formatted(shorten(item.description())));
                continue;
            }

            AiOpportunity entity = new AiOpportunity();
            entity.setProcess(process);
            entity.setActivity(resolveActivity(item.activityName(), activities, "AI opportunity", warnings));
            entity.setDescription(item.description());
            entity.setAiCapability(item.aiCapability());
            entity.setAutomationPotential(item.automationPotential());
            entity.setBusinessBenefit(item.businessBenefit());
            entity.setRisk(item.risk());
            entity.setReasoningNote(item.reasoningNote());
            entity.setRootCause(item.rootCause());
            entity.setHumanOversight(item.humanOversight());
            entity.setDataRequirement(item.dataRequirement());
            entity.setSuccessMetric(item.successMetric());
            entity.setGroundingScore(groundingScore);
            entity.setDisplayOrder(order++);
            entity.getEvidence().addAll(resolveEvidence(item, groundingSnippets, warnings));
            entity.getCitedClaims().addAll(cited);
            entities.add(entity);
        }
        return opportunityRepository.saveAll(entities);
    }

    /**
     * Turns the citation numbers into rows, keeping only the ones that resolve.
     *
     * <p>Unverified claims are linked as well as verified ones, because the citation is still true —
     * the model did rely on that claim, and hiding the link would hide the weakness. What they do not
     * do is count towards the grounding score.
     */
    private List<EvidenceClaim> resolveClaims(
            List<Integer> citedIndices,
            Map<Integer, EvidenceClaim> claims,
            String context,
            List<String> warnings) {

        List<EvidenceClaim> resolved = new ArrayList<>();
        for (Integer index : citedIndices) {
            EvidenceClaim claim = claims.get(index);
            if (claim == null) {
                warnings.add("Ignored citation [%d] on \"%s\": no evidence with that number exists in this run."
                        .formatted(index, shorten(context)));
                continue;
            }
            if (!resolved.contains(claim)) {
                resolved.add(claim);
            }
        }
        return resolved;
    }

    /**
     * How well supported a recommendation is, 0-100, computed rather than claimed.
     *
     * <p>Only quote-verified claims count. Beyond that it rewards the two things that make evidence
     * stronger: the confidence already computed for each claim (which folds in source credibility and
     * recency), and having more than one independent source rather than leaning on a single page.
     */
    private int groundingScoreFor(List<EvidenceClaim> cited) {
        List<EvidenceClaim> verified = cited.stream().filter(EvidenceClaim::isQuoteVerified).toList();
        if (verified.isEmpty()) {
            return 0;
        }
        double bestConfidence = verified.stream().mapToDouble(EvidenceClaim::getConfidence).max().orElse(0);
        long distinctDomains = verified.stream()
                .map(claim -> claim.getSource().getDomain())
                .distinct()
                .count();
        double breadthBonus = Math.min(20, (distinctDomains - 1) * 10);
        double corroborationBonus = Math.min(10,
                verified.stream().mapToInt(EvidenceClaim::getCorroborationCount).max().orElse(0) * 5);

        return (int) Math.round(Math.min(100, bestConfidence * 0.7 + breadthBonus + corroborationBonus));
    }

    /**
     * Links an opportunity to the curated snippets that supported it.
     *
     * <p>Only snippets actually retrieved for this run are eligible. If the model cites a title that
     * was never shown to it, the citation is discarded and recorded as a warning — the alternative,
     * storing an unverifiable source, would make the Evidence tab a liability.
     */
    private List<KnowledgeSnippet> resolveEvidence(
            NormalizedAnalysis.Opportunity item, List<KnowledgeSnippet> groundingSnippets, List<String> warnings) {

        List<KnowledgeSnippet> resolved = new ArrayList<>();
        for (String title : item.supportingSnippetTitles()) {
            Optional<KnowledgeSnippet> match =
                    NameMatcher.resolve(title, groundingSnippets, KnowledgeSnippet::getTitle, 0.6);
            if (match.isEmpty()) {
                warnings.add("Ignored citation \"%s\": no snippet with that title was supplied to the model."
                        .formatted(shorten(title)));
                continue;
            }
            if (!resolved.contains(match.get())) {
                resolved.add(match.get());
            }
        }
        return resolved;
    }

    private List<FutureActivity> persistFutureActivities(BusinessProcess process, NormalizedAnalysis analysis) {
        List<FutureActivity> entities = new ArrayList<>();
        for (NormalizedAnalysis.FutureStep item : analysis.futureActivities()) {
            FutureActivity entity = new FutureActivity();
            entity.setProcess(process);
            entity.setName(item.name());
            entity.setSequenceOrder(item.sequenceOrder());
            entity.setDescription(item.description());
            entity.setHumanResponsibility(item.humanResponsibility());
            entity.setAiResponsibility(item.aiResponsibility());
            entity.setResponsibilityType(item.responsibilityType());
            entity.setHandoffNote(item.handoffNote());
            entity.setFailureMode(item.failureMode());
            entity.setReplacesActivity(item.replacesActivity());
            entity.setCycleTimeNote(item.cycleTimeNote());
            entities.add(entity);
        }
        return futureActivityRepository.saveAll(entities);
    }

    private List<AiIntervention> persistInterventions(
            BusinessProcess process,
            List<FutureActivity> futureActivities,
            List<AiOpportunity> opportunities,
            NormalizedAnalysis analysis,
            List<String> warnings) {

        List<AiIntervention> entities = new ArrayList<>();
        for (NormalizedAnalysis.Intervention item : analysis.interventions()) {
            Optional<FutureActivity> futureActivity = NameMatcher.resolve(
                    item.futureActivityName(), futureActivities, FutureActivity::getName, nameMatchThreshold);
            if (futureActivity.isEmpty()) {
                warnings.add("Dropped intervention \"%s\": it referenced future activity \"%s\", which was not generated."
                        .formatted(shorten(item.description()), shorten(item.futureActivityName())));
                continue;
            }

            Optional<AiOpportunity> opportunity = NameMatcher.resolve(
                    item.relatedOpportunityDescription(), opportunities, AiOpportunity::getDescription,
                    nameMatchThreshold);
            if (opportunity.isEmpty() && item.relatedOpportunityDescription() != null) {
                warnings.add("Intervention \"%s\" could not be linked to a stored AI opportunity."
                        .formatted(shorten(item.description())));
            }

            AiIntervention entity = new AiIntervention();
            entity.setProcess(process);
            entity.setFutureActivity(futureActivity.get());
            entity.setRelatedAiOpportunity(opportunity.orElse(null));
            entity.setInterventionType(item.interventionType());
            entity.setDescription(item.description());
            entities.add(entity);
        }
        return interventionRepository.saveAll(entities);
    }

    /** The reviewer's verdicts, one per opportunity it could be matched to. */
    private int persistReviews(
            List<AiOpportunity> opportunities, NormalizedAnalysis analysis, List<String> warnings) {

        List<OpportunityScore> entities = new ArrayList<>();
        for (NormalizedAnalysis.Critique critique : analysis.critiques()) {
            Optional<AiOpportunity> matched = NameMatcher.resolve(
                    critique.opportunityDescription(), opportunities, AiOpportunity::getDescription,
                    nameMatchThreshold);
            if (matched.isEmpty()) {
                warnings.add("Dropped a review that no stored opportunity matched (\"%s\")."
                        .formatted(shorten(critique.opportunityDescription())));
                continue;
            }
            AiOpportunity opportunity = matched.get();
            OpportunityScore score = new OpportunityScore();
            score.setOpportunity(opportunity);
            score.setFeasibility(critique.feasibility());
            score.setEvidenceStrength(critique.evidenceStrength());
            score.setBusinessImpact(critique.businessImpact());
            score.setRiskLevel(critique.riskLevel());
            score.setImplementationEffort(critique.implementationEffort());
            score.setVerdict(critique.verdict());
            score.setCritique(critique.critique());
            score.setGroundedClaimCount((int) opportunity.getCitedClaims().stream()
                    .filter(EvidenceClaim::isQuoteVerified)
                    .count());
            score.setConfidence(confidenceFor(critique, opportunity));
            entities.add(score);
        }
        return scoreRepository.saveAll(entities).size();
    }

    /**
     * How much confidence a recommendation deserves, 0-100.
     *
     * <p>Combines what the reviewer thought with what the evidence actually supports, then applies
     * the one adjustment that matters most: a proposal the reviewer flagged as risky is discounted,
     * because in this domain being confidently wrong about a person is the expensive failure.
     */
    private double confidenceFor(NormalizedAnalysis.Critique critique, AiOpportunity opportunity) {
        double reviewer = (critique.feasibility() + critique.evidenceStrength() + critique.businessImpact())
                / 15.0 * 100.0;
        double grounding = opportunity.getGroundingScore();
        double verdictWeight = switch (critique.verdict()) {
            case STRONG -> 1.0;
            case SOUND -> 0.9;
            case QUALIFIED -> 0.7;
            case WEAK -> 0.45;
            case REJECTED -> 0.2;
        };
        double riskPenalty = critique.riskLevel() * 3.0;
        double blended = (reviewer * 0.55 + grounding * 0.45) * verdictWeight - riskPenalty;
        return Math.round(Math.max(0, Math.min(100, blended)) * 100.0) / 100.0;
    }

    /** The impact model: the model's inputs, this application's arithmetic. */
    private int persistImpacts(
            BusinessProcess process,
            List<Activity> activities,
            List<AiOpportunity> opportunities,
            NormalizedAnalysis analysis,
            List<String> warnings) {

        List<ImpactEstimate> entities = new ArrayList<>();
        int order = 0;
        for (NormalizedAnalysis.Impact item : analysis.impacts()) {
            ImpactCalculator.Computed computed = impactCalculator.compute(item);

            ImpactEstimate entity = new ImpactEstimate();
            entity.setProcess(process);
            entity.setOpportunity(NameMatcher.resolve(item.opportunityDescription(), opportunities,
                    AiOpportunity::getDescription, nameMatchThreshold).orElse(null));
            entity.setActivity(resolveActivity(item.activityName(), activities, "impact estimate", warnings));
            entity.setLabel(item.label());
            entity.setVolumePerMonth(item.volumePerMonth());
            entity.setMinutesPerItem(item.minutesPerItem());
            entity.setAutomationShare(item.automationShare());
            entity.setHourlyCostInr(item.hourlyCostInr());
            entity.setHoursSavedPerMonth(computed.hoursSavedPerMonth());
            entity.setCostSavedPerMonthInr(computed.netSavingPerMonthInr());
            entity.setErrorReductionPercent(item.errorReductionPercent());
            entity.setOneOffEffortDays(item.oneOffEffortDays());
            entity.setRunCostPerMonthInr(computed.runCostPerMonthInr());
            entity.setPaybackMonths(computed.paybackMonths());
            entity.setBasis(item.basis());
            entity.setAssumptions(item.assumptions());
            entity.setDisplayOrder(order++);
            entities.add(entity);
        }
        return impactRepository.saveAll(entities).size();
    }

    private int persistRisks(
            BusinessProcess process,
            List<AiOpportunity> opportunities,
            Map<Integer, EvidenceClaim> claims,
            NormalizedAnalysis analysis,
            List<String> warnings) {

        List<RiskItem> entities = new ArrayList<>();
        int order = 0;
        for (NormalizedAnalysis.Risk item : analysis.risks()) {
            RiskItem entity = new RiskItem();
            entity.setProcess(process);
            entity.setOpportunity(NameMatcher.resolve(item.opportunityDescription(), opportunities,
                    AiOpportunity::getDescription, nameMatchThreshold).orElse(null));
            entity.setTitle(item.title());
            entity.setDescription(item.description());
            entity.setCategory(item.category());
            entity.setLikelihood(item.likelihood());
            entity.setImpact(item.impact());
            entity.setSeverityScore(item.likelihood() * item.impact());
            entity.setMitigation(item.mitigation());
            entity.setOwnerRole(item.ownerRole());
            entity.setObligation(item.obligation());
            entity.setDisplayOrder(order++);
            entity.getCitedClaims().addAll(resolveClaims(item.citedEvidence(), claims, item.title(), warnings));
            entities.add(entity);
        }
        return riskRepository.saveAll(entities).size();
    }

    private int persistRoadmap(
            BusinessProcess process, List<AiOpportunity> opportunities, NormalizedAnalysis analysis) {

        List<RoadmapItem> entities = new ArrayList<>();
        int order = 0;
        for (NormalizedAnalysis.RoadmapEntry item : analysis.roadmap()) {
            RoadmapItem entity = new RoadmapItem();
            entity.setProcess(process);
            entity.setOpportunity(NameMatcher.resolve(item.opportunityDescription(), opportunities,
                    AiOpportunity::getDescription, nameMatchThreshold).orElse(null));
            entity.setWave(item.wave());
            entity.setTitle(item.title());
            entity.setDescription(item.description());
            entity.setEffort(item.effort());
            entity.setImpact(item.impact());
            entity.setDurationWeeks(item.durationWeeks());
            entity.setDependsOn(item.dependsOn());
            entity.setSuccessMetric(item.successMetric());
            entity.setDisplayOrder(order++);
            entities.add(entity);
        }
        return roadmapRepository.saveAll(entities).size();
    }

    private void persistScorecard(BusinessProcess process, PersistCommand command, List<String> warnings) {
        if (command.scorecard() == null || command.analysisRunId() == null) {
            return;
        }
        Optional<AnalysisRun> run = runRepository.findById(command.analysisRunId());
        if (run.isEmpty()) {
            warnings.add("The scorecard could not be stored: its analysis run was not found.");
            return;
        }
        ScorecardCalculator.Scorecard computed = command.scorecard();
        AnalysisScorecard entity = new AnalysisScorecard();
        entity.setAnalysisRun(run.get());
        entity.setProcess(process);
        entity.setCoverageScore(computed.coverage());
        entity.setGroundingScore(computed.grounding());
        entity.setCorroborationScore(computed.corroboration());
        entity.setAgreementScore(computed.agreement());
        entity.setSpecificityScore(computed.specificity());
        entity.setTraceabilityScore(computed.traceability());
        entity.setOverallScore(computed.overall());
        entity.setGrade(computed.grade());
        entity.setMetrics(computed.metricsJson());
        scorecardRepository.save(entity);
    }

    private Activity resolveActivity(
            String activityName, List<Activity> activities, String context, List<String> warnings) {

        if (activityName == null || activityName.isBlank()) {
            return null;
        }
        Optional<Activity> match =
                NameMatcher.resolve(activityName, activities, Activity::getName, nameMatchThreshold);
        if (match.isEmpty()) {
            warnings.add("A %s referenced activity \"%s\", which is not part of this process; stored without an activity link."
                    .formatted(context, shorten(activityName)));
        }
        return match.orElse(null);
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "...";
    }
}
