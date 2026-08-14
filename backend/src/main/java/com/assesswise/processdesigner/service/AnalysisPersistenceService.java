package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.AiIntervention;
import com.assesswise.processdesigner.domain.AiOpportunity;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.FutureActivity;
import com.assesswise.processdesigner.domain.KnowledgeSnippet;
import com.assesswise.processdesigner.domain.ProblemSource;
import com.assesswise.processdesigner.domain.ProcessStatus;
import com.assesswise.processdesigner.exception.ResourceNotFoundException;
import com.assesswise.processdesigner.repository.ActivityRepository;
import com.assesswise.processdesigner.repository.AiInterventionRepository;
import com.assesswise.processdesigner.repository.AiOpportunityRepository;
import com.assesswise.processdesigner.repository.BusinessProcessRepository;
import com.assesswise.processdesigner.repository.FutureActivityRepository;
import com.assesswise.processdesigner.repository.KnowledgeSnippetRepository;
import com.assesswise.processdesigner.repository.ProblemRepository;
import com.assesswise.processdesigner.service.KnowledgeRetrievalService.ScoredSnippet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * <p>Deliberately separate from {@link AnalysisService}: the model call takes tens of seconds and
 * must not hold a database transaction open across it — especially against a serverless Postgres
 * with a small connection allowance. The whole write below happens in one short transaction, so a
 * re-analysis is atomic: either the new future state replaces the old one completely, or nothing
 * changes.
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
    private final double nameMatchThreshold;

    public AnalysisPersistenceService(
            BusinessProcessRepository processRepository,
            ActivityRepository activityRepository,
            ProblemRepository problemRepository,
            AiOpportunityRepository opportunityRepository,
            FutureActivityRepository futureActivityRepository,
            AiInterventionRepository interventionRepository,
            KnowledgeSnippetRepository snippetRepository,
            AppProperties properties) {
        this.processRepository = processRepository;
        this.activityRepository = activityRepository;
        this.problemRepository = problemRepository;
        this.opportunityRepository = opportunityRepository;
        this.futureActivityRepository = futureActivityRepository;
        this.interventionRepository = interventionRepository;
        this.snippetRepository = snippetRepository;
        this.nameMatchThreshold = properties.analysis().nameMatchThreshold();
    }

    public record PersistResult(
            int problems,
            int opportunities,
            int futureActivities,
            int interventions,
            List<String> warnings) {}

    /**
     * Replaces the AI-generated state of a process. Re-running an analysis is therefore idempotent:
     * previous opportunities, future activities and interventions are removed first, so no
     * duplicates or orphans accumulate.
     */
    @Transactional
    public PersistResult replaceAnalysis(
            UUID processId, NormalizedAnalysis analysis, List<ScoredSnippet> retrievedSnippets) {

        List<String> warnings = new ArrayList<>();

        // Order matters: interventions reference both future activities and opportunities.
        int removedInterventions = interventionRepository.deleteByProcessId(processId);
        int removedFuture = futureActivityRepository.deleteByProcessId(processId);
        int removedOpportunities = opportunityRepository.deleteByProcessId(processId);
        int removedProblems = problemRepository.deleteByProcessIdAndSource(processId, ProblemSource.AI_GENERATED);
        if (removedInterventions + removedFuture + removedOpportunities + removedProblems > 0) {
            log.info("Re-analysis of process {}: cleared {} problems, {} opportunities, {} future activities, "
                            + "{} interventions", processId, removedProblems, removedOpportunities, removedFuture,
                    removedInterventions);
        }

        BusinessProcess process = processRepository.findById(processId)
                .orElseThrow(() -> ResourceNotFoundException.of("Process", processId));
        List<Activity> activities = activityRepository.findByProcessIdOrderBySequenceOrderAsc(processId);
        // Re-read the retrieved snippets inside this transaction: the copies handed in were loaded
        // during retrieval and are detached, and they are about to become join-table rows.
        List<KnowledgeSnippet> groundingSnippets = snippetRepository.findAllById(
                retrievedSnippets.stream().map(scored -> scored.snippet().getId()).toList());

        List<com.assesswise.processdesigner.domain.Problem> problems =
                persistProblems(process, activities, analysis, warnings);
        List<AiOpportunity> opportunities =
                persistOpportunities(process, activities, groundingSnippets, analysis, warnings);
        List<FutureActivity> futureActivities = persistFutureActivities(process, analysis);
        List<AiIntervention> interventions =
                persistInterventions(process, futureActivities, opportunities, analysis, warnings);

        process.setStatus(ProcessStatus.ANALYZED);
        process.setLastAnalyzedAt(Instant.now());
        processRepository.save(process);

        return new PersistResult(
                problems.size(), opportunities.size(), futureActivities.size(), interventions.size(), warnings);
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
            entities.add(entity);
        }
        return problemRepository.saveAll(entities);
    }

    private List<AiOpportunity> persistOpportunities(
            BusinessProcess process,
            List<Activity> activities,
            List<KnowledgeSnippet> groundingSnippets,
            NormalizedAnalysis analysis,
            List<String> warnings) {

        List<AiOpportunity> entities = new ArrayList<>();
        int order = 0;
        for (NormalizedAnalysis.Opportunity item : analysis.opportunities()) {
            AiOpportunity entity = new AiOpportunity();
            entity.setProcess(process);
            entity.setActivity(resolveActivity(item.activityName(), activities, "AI opportunity", warnings));
            entity.setDescription(item.description());
            entity.setAiCapability(item.aiCapability());
            entity.setAutomationPotential(item.automationPotential());
            entity.setBusinessBenefit(item.businessBenefit());
            entity.setRisk(item.risk());
            entity.setReasoningNote(item.reasoningNote());
            entity.setDisplayOrder(order++);
            entity.getEvidence().addAll(resolveEvidence(item, groundingSnippets, warnings));
            entities.add(entity);
        }
        return opportunityRepository.saveAll(entities);
    }

    /**
     * Links an opportunity to the snippets that supported it.
     *
     * <p>Only snippets actually retrieved for this run are eligible. If the model cites a title
     * that was never shown to it, the citation is discarded and recorded as a warning — the
     * alternative, storing an unverifiable source, would make the Evidence tab a liability.
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
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "…";
    }
}
