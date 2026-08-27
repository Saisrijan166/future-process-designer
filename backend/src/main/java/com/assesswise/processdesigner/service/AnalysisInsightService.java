package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.AnalysisRun;
import com.assesswise.processdesigner.domain.AnalysisRunStatus;
import com.assesswise.processdesigner.domain.AnalysisStage;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.domain.ResearchRun;
import com.assesswise.processdesigner.domain.ResearchSource;
import com.assesswise.processdesigner.domain.StageStatus;
import com.assesswise.processdesigner.dto.ActiveRunDto;
import com.assesswise.processdesigner.dto.AnalysisStageDto;
import com.assesswise.processdesigner.dto.ImpactEstimateDto;
import com.assesswise.processdesigner.dto.OpportunityScoreDto;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.dto.ResearchRunDto;
import com.assesswise.processdesigner.dto.RiskItemDto;
import com.assesswise.processdesigner.dto.RoadmapItemDto;
import com.assesswise.processdesigner.dto.ScorecardDto;
import com.assesswise.processdesigner.mapper.DomainMapper;
import com.assesswise.processdesigner.repository.AnalysisRunRepository;
import com.assesswise.processdesigner.repository.AnalysisScorecardRepository;
import com.assesswise.processdesigner.repository.AnalysisStageRepository;
import com.assesswise.processdesigner.repository.EvidenceClaimRepository;
import com.assesswise.processdesigner.repository.ImpactEstimateRepository;
import com.assesswise.processdesigner.repository.OpportunityScoreRepository;
import com.assesswise.processdesigner.repository.ResearchQueryRepository;
import com.assesswise.processdesigner.repository.ResearchRunRepository;
import com.assesswise.processdesigner.repository.ResearchSourceRepository;
import com.assesswise.processdesigner.repository.RiskItemRepository;
import com.assesswise.processdesigner.repository.RoadmapItemRepository;
import com.assesswise.processdesigner.service.pipeline.StagedAnalysisPipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for everything the staged pipeline produces beyond the current and future state.
 *
 * <p>Separate from {@code ProcessService} because the questions are different. That class answers
 * "what is this process?"; this one answers "what did the analysis conclude, what did it rest on,
 * and how good was it?" — reviews, impact figures, risks, the delivery plan, the research pass and
 * the scorecard. Keeping them apart also keeps the process detail query from acquiring eleven more
 * repositories.
 */
@Service
public class AnalysisInsightService {

    private final OpportunityScoreRepository scoreRepository;
    private final ImpactEstimateRepository impactRepository;
    private final RiskItemRepository riskRepository;
    private final RoadmapItemRepository roadmapRepository;
    private final AnalysisScorecardRepository scorecardRepository;
    private final AnalysisStageRepository stageRepository;
    private final AnalysisRunRepository runRepository;
    private final ResearchRunRepository researchRunRepository;
    private final ResearchQueryRepository researchQueryRepository;
    private final ResearchSourceRepository researchSourceRepository;
    private final EvidenceClaimRepository claimRepository;
    private final DomainMapper mapper;

    /**
     * How many stages a run will attempt, so progress reads as "4 of 10".
     *
     * <p>Taken from the configured pipeline rather than from the run row, because a run only records
     * which pipeline produced it once it has finished — and this is a question about a run that has
     * not.
     */
    private final int stagesTotal;

    public AnalysisInsightService(
            OpportunityScoreRepository scoreRepository,
            ImpactEstimateRepository impactRepository,
            RiskItemRepository riskRepository,
            RoadmapItemRepository roadmapRepository,
            AnalysisScorecardRepository scorecardRepository,
            AnalysisStageRepository stageRepository,
            AnalysisRunRepository runRepository,
            ResearchRunRepository researchRunRepository,
            ResearchQueryRepository researchQueryRepository,
            ResearchSourceRepository researchSourceRepository,
            EvidenceClaimRepository claimRepository,
            DomainMapper mapper,
            StagedAnalysisPipeline pipeline,
            AppProperties properties) {
        this.scoreRepository = scoreRepository;
        this.impactRepository = impactRepository;
        this.riskRepository = riskRepository;
        this.roadmapRepository = roadmapRepository;
        this.scorecardRepository = scorecardRepository;
        this.stageRepository = stageRepository;
        this.runRepository = runRepository;
        this.researchRunRepository = researchRunRepository;
        this.researchQueryRepository = researchQueryRepository;
        this.researchSourceRepository = researchSourceRepository;
        this.claimRepository = claimRepository;
        this.mapper = mapper;
        this.stagesTotal = "single".equalsIgnoreCase(properties.analysis().pipeline())
                ? 1
                : pipeline.stageCount();
    }

    /** Everything the detail view needs beyond the current and future state, in one read. */
    public record Insights(
            Map<UUID, OpportunityScoreDto> reviewsByOpportunity,
            Map<UUID, ImpactEstimateDto> impactsByOpportunity,
            List<ImpactEstimateDto> impacts,
            List<RiskItemDto> risks,
            List<RoadmapItemDto> roadmap,
            ScorecardDto scorecard,
            ProcessDetailDto.ResearchSummaryDto research) {}

    @Transactional(readOnly = true)
    public Insights forProcess(UUID processId) {
        Map<UUID, OpportunityScoreDto> reviews = new LinkedHashMap<>();
        scoreRepository.findByProcessId(processId)
                .forEach(score -> reviews.put(score.getAiOpportunityId(), mapper.toDto(score)));

        List<ImpactEstimateDto> impacts = impactRepository.findByProcessIdOrderByDisplayOrderAsc(processId).stream()
                .map(mapper::toDto)
                .toList();
        Map<UUID, ImpactEstimateDto> impactsByOpportunity = new LinkedHashMap<>();
        impacts.stream()
                .filter(impact -> impact.opportunityId() != null)
                // First wins: one estimate per opportunity is the contract, and a duplicate should not
                // silently replace the one the interface already showed.
                .forEach(impact -> impactsByOpportunity.putIfAbsent(impact.opportunityId(), impact));

        List<RiskItemDto> risks = riskRepository.findWithClaimsByProcessId(processId).stream()
                .map(mapper::toDto)
                .toList();
        List<RoadmapItemDto> roadmap = roadmapRepository.findByProcessIdOrderByWaveAscDisplayOrderAsc(processId)
                .stream()
                .map(mapper::toDto)
                .toList();

        ScorecardDto scorecard = scorecardRepository.findFirstByProcessIdOrderByCreatedAtDesc(processId)
                .map(mapper::toDto)
                .orElse(null);

        return new Insights(reviews, impactsByOpportunity, impacts, risks, roadmap, scorecard,
                researchSummary(processId));
    }

    /**
     * The headline of the research pass behind the analysis that is actually stored.
     *
     * <p>Deliberately the last <em>succeeded</em> run's research rather than the most recent research
     * of any kind: a failed re-run would otherwise relabel the evidence for rows that a different
     * research pass produced.
     */
    @Transactional(readOnly = true)
    public ProcessDetailDto.ResearchSummaryDto researchSummary(UUID processId) {
        return latestSuccessfulResearchRun(processId)
                .map(run -> mapper.toSummary(run,
                        researchSourceRepository.findByResearchRunIdOrderByDisplayOrderAsc(run.getId()).size()))
                .orElse(null);
    }

    /** The full research pass: queries, sources with credibility, and every quoted claim. */
    @Transactional(readOnly = true)
    public Optional<ResearchRunDto> researchRun(UUID processId) {
        return latestSuccessfulResearchRun(processId).map(run -> {
            List<ResearchSource> sources =
                    researchSourceRepository.findByResearchRunIdOrderByDisplayOrderAsc(run.getId());
            List<EvidenceClaim> claims = claimRepository.findWithSourcesByRun(run.getId());
            return mapper.toDto(run,
                    researchQueryRepository.findByResearchRunIdOrderByDisplayOrderAsc(run.getId()),
                    sources,
                    claims);
        });
    }

    @Transactional(readOnly = true)
    public Optional<ResearchRunDto> researchRunById(UUID researchRunId) {
        return researchRunRepository.findById(researchRunId).map(run -> mapper.toDto(run,
                researchQueryRepository.findByResearchRunIdOrderByDisplayOrderAsc(run.getId()),
                researchSourceRepository.findByResearchRunIdOrderByDisplayOrderAsc(run.getId()),
                claimRepository.findWithSourcesByRun(run.getId())));
    }

    /**
     * The analysis running for this process right now, if there is one.
     *
     * <p>Read from the database rather than from any in-memory registry, and that is the point: the
     * pipeline commits each stage's start and finish in its own transaction, so a run is visible to
     * a different tab, a reloaded page, or a second instance behind a load balancer. Without this,
     * a run that outlived the tab that started it was invisible — the page showed nothing happening
     * and the button refused to work, with no way to find out why.
     */
    @Transactional(readOnly = true)
    public Optional<ActiveRunDto> activeRun(UUID processId) {
        return runRepository
                .findFirstByProcessIdAndStatusOrderByStartedAtDesc(processId, AnalysisRunStatus.RUNNING)
                .map(this::toActiveRun);
    }

    private ActiveRunDto toActiveRun(AnalysisRun run) {
        List<AnalysisStage> rows = stageRepository.findByAnalysisRunIdOrderByDisplayOrderAsc(run.getId());

        List<ActiveRunDto.StageProgressDto> stages = rows.stream()
                .map(stage -> new ActiveRunDto.StageProgressDto(
                        stage.getStageId(),
                        stage.getTitle(),
                        stage.getStatus(),
                        stage.getDurationMs(),
                        stage.getSummary()))
                .toList();

        // A stage row is inserted when the stage starts and updated when it ends, so exactly one
        // row is RUNNING while the pipeline is between commits — that is the current stage.
        AnalysisStage current = rows.stream()
                .filter(stage -> stage.getStatus() == StageStatus.RUNNING)
                .findFirst()
                .orElse(null);
        long finished = rows.stream().filter(stage -> stage.getStatus() != StageStatus.RUNNING).count();

        return new ActiveRunDto(
                run.getId(),
                processId(run),
                run.getProcess().getName(),
                run.getStartedAt(),
                Duration.between(run.getStartedAt(), Instant.now()).toMillis(),
                (int) finished,
                stagesTotal,
                current == null ? null : current.getStageId(),
                current == null ? null : current.getTitle(),
                stages);
    }

    private static UUID processId(AnalysisRun run) {
        return run.getProcess().getId();
    }

    /** Every process with an analysis running right now, for the dashboard. */
    @Transactional(readOnly = true)
    public Set<UUID> processesBeingAnalysed() {
        return Set.copyOf(runRepository.findProcessIdsByStatus(AnalysisRunStatus.RUNNING));
    }

    /** Every stage of one run, in order, with prompts and responses. */
    @Transactional(readOnly = true)
    public List<AnalysisStageDto> stages(UUID analysisRunId) {
        return stageRepository.findByAnalysisRunIdOrderByDisplayOrderAsc(analysisRunId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScorecardDto scorecardForRun(UUID analysisRunId) {
        return scorecardRepository.findById(analysisRunId).map(mapper::toDto).orElse(null);
    }

    /**
     * Finds the research behind the last analysis that succeeded.
     *
     * <p>Two routes, because a run records its research either way: the forward link on the analysis
     * run, and failing that the research run's own back-reference. Belt and braces on purpose — the
     * evidence view returning nothing because one of two nullable columns was missed would be a
     * confusing way to lose a whole tab.
     */
    private Optional<ResearchRun> latestSuccessfulResearchRun(UUID processId) {
        Optional<ResearchRun> viaRun = runRepository
                .findFirstByProcessIdAndStatusOrderByStartedAtDesc(processId, AnalysisRunStatus.SUCCEEDED)
                .map(run -> run.getResearchRunId())
                .flatMap(researchRunRepository::findById);
        if (viaRun.isPresent()) {
            return viaRun;
        }
        return researchRunRepository.findFirstByProcessIdOrderByStartedAtDesc(processId)
                .filter(run -> run.getClaimCount() > 0);
    }
}
