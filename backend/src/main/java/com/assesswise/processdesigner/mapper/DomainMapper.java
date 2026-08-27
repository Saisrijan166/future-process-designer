package com.assesswise.processdesigner.mapper;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.AnalysisScorecard;
import com.assesswise.processdesigner.domain.AnalysisStage;
import com.assesswise.processdesigner.domain.ClaimRelation;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.domain.ImpactEstimate;
import com.assesswise.processdesigner.domain.OpportunityScore;
import com.assesswise.processdesigner.domain.ResearchQuery;
import com.assesswise.processdesigner.domain.ResearchRun;
import com.assesswise.processdesigner.domain.ResearchSource;
import com.assesswise.processdesigner.domain.RiskItem;
import com.assesswise.processdesigner.domain.RoadmapItem;
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
import com.assesswise.processdesigner.dto.AnalysisStageDto;
import com.assesswise.processdesigner.dto.EvidenceClaimDto;
import com.assesswise.processdesigner.dto.ImpactEstimateDto;
import com.assesswise.processdesigner.dto.OpportunityScoreDto;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.dto.ResearchRunDto;
import com.assesswise.processdesigner.dto.ResearchSourceDto;
import com.assesswise.processdesigner.dto.RiskItemDto;
import com.assesswise.processdesigner.dto.RoadmapItemDto;
import com.assesswise.processdesigner.dto.ScorecardDto;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper;

    public DomainMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
                problem.getSource(),
                problem.getRootCause(),
                problem.getEvidenceNote());
    }

    public AiOpportunityDto toDto(AiOpportunity opportunity) {
        return toDto(opportunity, null, null);
    }

    /**
     * @param review the second model's verdict, or null when the review stage did not run
     * @param impact what this is worth per month, or null when it was not quantified
     */
    public AiOpportunityDto toDto(
            AiOpportunity opportunity, OpportunityScoreDto review, ImpactEstimateDto impact) {

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
                opportunity.getRootCause(),
                opportunity.getHumanOversight(),
                opportunity.getDataRequirement(),
                opportunity.getSuccessMetric(),
                opportunity.getGroundingScore(),
                opportunity.getEvidence().stream()
                        .map(this::toDto)
                        .sorted(Comparator.comparing(KnowledgeSnippetDto::title))
                        .toList(),
                opportunity.getCitedClaims().stream()
                        .map(this::toDto)
                        .sorted(Comparator.comparingInt(EvidenceClaimDto::citationIndex))
                        .toList(),
                review,
                impact);
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
                futureActivity.getHandoffNote(),
                futureActivity.getFailureMode(),
                futureActivity.getReplacesActivity(),
                futureActivity.getCycleTimeNote(),
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
        return toDto(run, null);
    }

    public AnalysisRunSummaryDto toDto(AnalysisRun run, ScorecardDto scorecard) {
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
                retrieved,
                run.getPipelineVersion(),
                run.getStageCount(),
                run.getTotalPromptTokens(),
                run.getTotalOutputTokens(),
                run.getCacheHitCount(),
                run.getThrottledMs(),
                run.getResearchRunId(),
                scorecard);
    }

    // =============================================================================================
    // Research, evidence and the staged pipeline
    // =============================================================================================

    /**
     * One claim with its source attached.
     *
     * <p>The source always travels with the claim. A quote without its publisher, its credibility and
     * whether it verified is not a citation, it is a sentence — and the interface must never be in a
     * position to render one without the other.
     */
    public EvidenceClaimDto toDto(EvidenceClaim claim) {
        return new EvidenceClaimDto(
                claim.getId(),
                claim.getCitationIndex(),
                claim.getClaimText(),
                claim.getQuote(),
                claim.isQuoteVerified(),
                claim.getQuoteMatchRatio(),
                claim.getQuoteStart(),
                claim.getClaimType(),
                claim.getTopic(),
                claim.getNumericValue(),
                claim.getNumericUnit(),
                claim.getAsOfDate(),
                claim.getConfidence(),
                claim.getCorroborationCount(),
                claim.getContradictionCount(),
                toDto(claim.getSource()));
    }

    public ResearchSourceDto toDto(ResearchSource source) {
        return new ResearchSourceDto(
                source.getId(),
                source.getConnectorId(),
                source.getUrl(),
                source.getDomain(),
                source.getTitle(),
                source.getSnippet(),
                source.getPublisher(),
                source.getPublishedAt(),
                source.getSourceType(),
                source.getRelevanceScore(),
                source.getCredibilityScore(),
                readCredibilityBreakdown(source.getCredibilityBreakdown()),
                source.getFetchStatus(),
                source.getHttpStatus(),
                source.getContentChars(),
                source.getClaimCount(),
                source.getFetchedAt());
    }

    public ResearchRunDto toDto(
            ResearchRun run,
            List<ResearchQuery> queries,
            List<ResearchSource> sources,
            List<EvidenceClaim> claims) {

        return new ResearchRunDto(
                run.getId(),
                run.getStatus(),
                TextSimilarity.splitList(run.getConnectorsUsed()),
                run.getQueryCount(),
                run.getHitCount(),
                run.getDocumentCount(),
                run.getClaimCount(),
                run.getVerifiedClaimCount(),
                run.getContradictionCount(),
                run.getDistinctDomainCount(),
                run.getCacheHitCount(),
                run.getDurationMs(),
                splitLines(run.getNotes()),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt(),
                queries.stream()
                        .map(query -> new ResearchRunDto.QueryDto(
                                query.getId(), query.getQueryText(), query.getIntent(), query.getOrigin(),
                                query.getHitCount(), query.getDurationMs()))
                        .toList(),
                sources.stream().map(this::toDto).toList(),
                claims.stream().map(this::toDto).toList());
    }

    public ProcessDetailDto.ResearchSummaryDto toSummary(ResearchRun run, int sourceCount) {
        if (run == null) {
            return null;
        }
        return new ProcessDetailDto.ResearchSummaryDto(
                run.getId(),
                run.getStatus().name(),
                sourceCount,
                run.getClaimCount(),
                run.getVerifiedClaimCount(),
                run.getContradictionCount(),
                run.getDistinctDomainCount(),
                run.getFinishedAt());
    }

    public AnalysisStageDto toDto(AnalysisStage stage) {
        return new AnalysisStageDto(
                stage.getId(),
                stage.getStageId(),
                stage.getTitle(),
                stage.getStatus(),
                stage.getDisplayOrder(),
                stage.getProvider(),
                stage.getModel(),
                stage.getPromptTokens(),
                stage.getOutputTokens(),
                stage.getDurationMs(),
                stage.getWaitedMs(),
                stage.isCached(),
                stage.getAttemptCount(),
                stage.getSummary(),
                stage.getPromptText(),
                stage.getResponseText(),
                stage.getErrorMessage(),
                splitLines(stage.getNotes()),
                stage.getStartedAt(),
                stage.getFinishedAt());
    }

    public OpportunityScoreDto toDto(OpportunityScore score) {
        return new OpportunityScoreDto(
                score.getFeasibility(),
                score.getEvidenceStrength(),
                score.getBusinessImpact(),
                score.getRiskLevel(),
                score.getImplementationEffort(),
                score.getConfidence(),
                score.getVerdict(),
                score.getCritique(),
                score.getReviewerModel(),
                score.getGroundedClaimCount());
    }

    public ImpactEstimateDto toDto(ImpactEstimate estimate) {
        return new ImpactEstimateDto(
                estimate.getId(),
                estimate.getOpportunity() == null ? null : estimate.getOpportunity().getId(),
                estimate.getActivity() == null ? null : estimate.getActivity().getId(),
                estimate.getLabel(),
                estimate.getVolumePerMonth(),
                estimate.getMinutesPerItem(),
                estimate.getAutomationShare(),
                estimate.getHourlyCostInr(),
                estimate.getHoursSavedPerMonth(),
                estimate.getCostSavedPerMonthInr(),
                estimate.getErrorReductionPercent(),
                estimate.getOneOffEffortDays(),
                estimate.getRunCostPerMonthInr(),
                estimate.getPaybackMonths(),
                estimate.getBasis(),
                estimate.getAssumptions());
    }

    public RiskItemDto toDto(RiskItem risk) {
        return new RiskItemDto(
                risk.getId(),
                risk.getOpportunity() == null ? null : risk.getOpportunity().getId(),
                risk.getTitle(),
                risk.getDescription(),
                risk.getCategory(),
                risk.getLikelihood(),
                risk.getImpact(),
                risk.getSeverityScore(),
                risk.getMitigation(),
                risk.getOwnerRole(),
                risk.getObligation(),
                risk.getCitedClaims().stream()
                        .map(this::toDto)
                        .sorted(Comparator.comparingInt(EvidenceClaimDto::citationIndex))
                        .toList());
    }

    public RoadmapItemDto toDto(RoadmapItem item) {
        return new RoadmapItemDto(
                item.getId(),
                item.getOpportunity() == null ? null : item.getOpportunity().getId(),
                item.getWave(),
                item.getTitle(),
                item.getDescription(),
                item.getEffort(),
                item.getImpact(),
                item.getDurationWeeks(),
                item.getDependsOn(),
                item.getSuccessMetric());
    }

    public ScorecardDto toDto(AnalysisScorecard scorecard) {
        if (scorecard == null) {
            return null;
        }
        return new ScorecardDto(
                scorecard.getAnalysisRunId(),
                scorecard.getCoverageScore(),
                scorecard.getGroundingScore(),
                scorecard.getCorroborationScore(),
                scorecard.getAgreementScore(),
                scorecard.getSpecificityScore(),
                scorecard.getTraceabilityScore(),
                scorecard.getOverallScore(),
                scorecard.getGrade(),
                readMetrics(scorecard.getMetrics()),
                scorecard.getCreatedAt());
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

    /**
     * Reads the stored credibility breakdown back into rows.
     *
     * <p>Stored as JSON because the components vary, and read back rather than recomputed so that
     * what the interface shows is what the run actually scored — recomputing would silently update
     * old runs when the scorer changes.
     */
    private List<ResearchSourceDto.CredibilityComponentDto> readCredibilityBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            var root = objectMapper.readTree(json).path("components");
            List<ResearchSourceDto.CredibilityComponentDto> components = new ArrayList<>();
            for (var node : root) {
                components.add(new ResearchSourceDto.CredibilityComponentDto(
                        node.path("label").asText(""),
                        node.path("points").asInt(0),
                        node.path("note").asText("")));
            }
            return components;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> readMetrics(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
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
