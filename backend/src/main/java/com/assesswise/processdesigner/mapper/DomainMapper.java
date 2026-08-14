package com.assesswise.processdesigner.mapper;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.AiIntervention;
import com.assesswise.processdesigner.domain.AiOpportunity;
import com.assesswise.processdesigner.domain.AnalysisRun;
import com.assesswise.processdesigner.domain.AnalysisRunSnippet;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.FutureActivity;
import com.assesswise.processdesigner.domain.KnowledgeSnippet;
import com.assesswise.processdesigner.domain.Problem;
import com.assesswise.processdesigner.domain.Role;
import com.assesswise.processdesigner.domain.SystemTool;
import com.assesswise.processdesigner.dto.ActivityDto;
import com.assesswise.processdesigner.dto.AiInterventionDto;
import com.assesswise.processdesigner.dto.AiOpportunityDto;
import com.assesswise.processdesigner.dto.AnalysisRunSummaryDto;
import com.assesswise.processdesigner.dto.FutureActivityDto;
import com.assesswise.processdesigner.dto.KnowledgeSnippetDto;
import com.assesswise.processdesigner.dto.ProblemDto;
import com.assesswise.processdesigner.dto.ProcessSummaryDto;
import com.assesswise.processdesigner.dto.RetrievedSnippetDto;
import com.assesswise.processdesigner.dto.RoleDto;
import com.assesswise.processdesigner.dto.SystemToolDto;
import com.assesswise.processdesigner.service.TextSimilarity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Entity → DTO translation. Kept in one place so the API contract is visible at a glance and JPA
 * entities never leak out of the service layer (which would otherwise serialise lazy proxies).
 */
@Component
public class DomainMapper {

    private static final int OPPORTUNITY_SUMMARY_LENGTH = 120;

    public ProcessSummaryDto toSummary(
            BusinessProcess process, long activityCount, long futureActivityCount, long opportunityCount) {
        return new ProcessSummaryDto(
                process.getId(),
                process.getName(),
                process.getIndustry(),
                process.getDescription(),
                process.getStatus(),
                process.getOrigin(),
                process.isSample(),
                activityCount,
                futureActivityCount,
                opportunityCount,
                process.getCreatedAt(),
                process.getLastAnalyzedAt());
    }

    public ActivityDto toDto(Activity activity, List<Problem> problemsForActivity) {
        return new ActivityDto(
                activity.getId(),
                activity.getName(),
                activity.getSequenceOrder(),
                activity.getDescription(),
                activity.getRoles().stream().map(Role::getName).sorted().toList(),
                activity.getSystems().stream().map(SystemTool::getName).sorted().toList(),
                problemsForActivity.stream().map(this::toDto).toList());
    }

    public ProblemDto toDto(Problem problem) {
        Activity activity = problem.getActivity();
        return new ProblemDto(
                problem.getId(),
                activity == null ? null : activity.getId(),
                activity == null ? null : activity.getName(),
                problem.getDescription(),
                problem.getSeverity(),
                problem.getSource());
    }

    public AiOpportunityDto toDto(AiOpportunity opportunity) {
        Activity activity = opportunity.getActivity();
        return new AiOpportunityDto(
                opportunity.getId(),
                activity == null ? null : activity.getId(),
                activity == null ? null : activity.getName(),
                opportunity.getDescription(),
                opportunity.getAiCapability(),
                opportunity.getAutomationPotential(),
                opportunity.getBusinessBenefit(),
                opportunity.getRisk(),
                opportunity.getReasoningNote(),
                opportunity.getEvidence().stream()
                        .map(this::toDto)
                        .sorted(Comparator.comparing(KnowledgeSnippetDto::title))
                        .toList());
    }

    public FutureActivityDto toDto(FutureActivity futureActivity, List<AiIntervention> interventions) {
        return new FutureActivityDto(
                futureActivity.getId(),
                futureActivity.getName(),
                futureActivity.getSequenceOrder(),
                futureActivity.getDescription(),
                futureActivity.getHumanResponsibility(),
                futureActivity.getAiResponsibility(),
                futureActivity.getResponsibilityType(),
                interventions.stream().map(this::toDto).toList());
    }

    public AiInterventionDto toDto(AiIntervention intervention) {
        FutureActivity futureActivity = intervention.getFutureActivity();
        AiOpportunity opportunity = intervention.getRelatedAiOpportunity();
        return new AiInterventionDto(
                intervention.getId(),
                futureActivity == null ? null : futureActivity.getId(),
                futureActivity == null ? null : futureActivity.getName(),
                opportunity == null ? null : opportunity.getId(),
                opportunity == null ? null : shorten(opportunity.getDescription()),
                intervention.getInterventionType(),
                intervention.getDescription());
    }

    public KnowledgeSnippetDto toDto(KnowledgeSnippet snippet) {
        return new KnowledgeSnippetDto(
                snippet.getId(),
                snippet.getTitle(),
                snippet.getSnippetText(),
                snippet.getSourceUrl(),
                snippet.getSourceType(),
                snippet.getPublisher(),
                TextSimilarity.splitList(snippet.getTags()),
                snippet.getRetrievedAt());
    }

    public RoleDto toDto(Role role) {
        return new RoleDto(role.getId(), role.getName());
    }

    public SystemToolDto toDto(SystemTool systemTool) {
        return new SystemToolDto(systemTool.getId(), systemTool.getName(), systemTool.getType());
    }

    public AnalysisRunSummaryDto toDto(AnalysisRun run) {
        if (run == null) {
            return null;
        }
        List<RetrievedSnippetDto> retrieved = run.getRetrievedSnippets().stream()
                .sorted(Comparator.comparingDouble(AnalysisRunSnippet::getRelevanceScore).reversed())
                .map(link -> new RetrievedSnippetDto(
                        toDto(link.getKnowledgeSnippet()),
                        link.getRelevanceScore(),
                        TextSimilarity.splitList(link.getMatchedTerms())))
                .toList();

        return new AnalysisRunSummaryDto(
                run.getId(),
                run.getStatus(),
                run.getProvider(),
                run.getModel(),
                run.isRepairAttempted(),
                splitLines(run.getValidationWarnings()),
                splitLines(run.getProviderNotes()),
                run.getErrorMessage(),
                run.getPromptTokens(),
                run.getOutputTokens(),
                run.getDurationMs(),
                run.getStartedAt(),
                run.getFinishedAt(),
                retrieved);
    }

    /** Groups problems by the activity they belong to, so each activity row carries its own. */
    public Map<UUID, List<Problem>> problemsByActivity(List<Problem> problems) {
        return problems.stream()
                .filter(problem -> problem.getActivity() != null)
                .collect(Collectors.groupingBy(problem -> problem.getActivity().getId()));
    }

    /** Groups interventions by the future activity they change. */
    public Map<UUID, List<AiIntervention>> interventionsByFutureActivity(List<AiIntervention> interventions) {
        return interventions.stream()
                .filter(intervention -> intervention.getFutureActivity() != null)
                .collect(Collectors.groupingBy(intervention -> intervention.getFutureActivity().getId()));
    }

    private List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private String shorten(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= OPPORTUNITY_SUMMARY_LENGTH
                ? value
                : value.substring(0, OPPORTUNITY_SUMMARY_LENGTH) + "…";
    }
}
