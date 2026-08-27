package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.AiIntervention;
import com.assesswise.processdesigner.domain.AiOpportunity;
import com.assesswise.processdesigner.domain.AnalysisRunStatus;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.FutureActivity;
import com.assesswise.processdesigner.domain.Problem;
import com.assesswise.processdesigner.domain.ProblemSource;
import com.assesswise.processdesigner.domain.ProcessOrigin;
import com.assesswise.processdesigner.domain.ProcessStatus;
import com.assesswise.processdesigner.domain.Role;
import com.assesswise.processdesigner.domain.SystemTool;
import com.assesswise.processdesigner.dto.ActivityDto;
import com.assesswise.processdesigner.dto.AnalysisRunSummaryDto;
import com.assesswise.processdesigner.dto.ComparisonDto;
import com.assesswise.processdesigner.dto.CreateProcessRequest;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.dto.ProcessPageDto;
import com.assesswise.processdesigner.dto.ProcessSummaryDto;
import com.assesswise.processdesigner.dto.RetrievedSnippetDto;
import com.assesswise.processdesigner.dto.UpdateProcessRequest;
import com.assesswise.processdesigner.exception.ResourceNotFoundException;
import com.assesswise.processdesigner.mapper.DomainMapper;
import com.assesswise.processdesigner.security.CurrentUser;
import com.assesswise.processdesigner.repository.ActivityRepository;
import com.assesswise.processdesigner.repository.AppUserRepository;
import com.assesswise.processdesigner.repository.AiInterventionRepository;
import com.assesswise.processdesigner.repository.AiOpportunityRepository;
import com.assesswise.processdesigner.repository.AnalysisRunRepository;
import com.assesswise.processdesigner.repository.BusinessProcessRepository;
import com.assesswise.processdesigner.repository.FutureActivityRepository;
import com.assesswise.processdesigner.repository.ProblemRepository;
import com.assesswise.processdesigner.repository.RoleRepository;
import com.assesswise.processdesigner.repository.SystemToolRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD and read models for processes. Knows nothing about AI — that is {@link AnalysisService}. */
@Service
public class ProcessService {

    private static final Logger log = LoggerFactory.getLogger(ProcessService.class);

    private final BusinessProcessRepository processRepository;
    private final ActivityRepository activityRepository;
    private final ProblemRepository problemRepository;
    private final AiOpportunityRepository opportunityRepository;
    private final FutureActivityRepository futureActivityRepository;
    private final AiInterventionRepository interventionRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final RoleRepository roleRepository;
    private final SystemToolRepository systemToolRepository;
    private final AppUserRepository userRepository;
    private final ProcessAccessService accessService;
    private final DomainMapper mapper;
    private final AnalysisInsightService insightService;

    public ProcessService(
            BusinessProcessRepository processRepository,
            ActivityRepository activityRepository,
            ProblemRepository problemRepository,
            AiOpportunityRepository opportunityRepository,
            FutureActivityRepository futureActivityRepository,
            AiInterventionRepository interventionRepository,
            AnalysisRunRepository analysisRunRepository,
            RoleRepository roleRepository,
            SystemToolRepository systemToolRepository,
            AppUserRepository userRepository,
            ProcessAccessService accessService,
            DomainMapper mapper,
            AnalysisInsightService insightService) {
        this.processRepository = processRepository;
        this.activityRepository = activityRepository;
        this.problemRepository = problemRepository;
        this.opportunityRepository = opportunityRepository;
        this.futureActivityRepository = futureActivityRepository;
        this.interventionRepository = interventionRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.roleRepository = roleRepository;
        this.systemToolRepository = systemToolRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.mapper = mapper;
        this.insightService = insightService;
    }

    /** Sort keys the API accepts, mapped to entity properties so the client cannot inject one. */
    private static final Map<String, Sort> SORTS = Map.of(
            "recent", Sort.by(Sort.Direction.DESC, "createdAt"),
            "oldest", Sort.by(Sort.Direction.ASC, "createdAt"),
            "name", Sort.by(Sort.Direction.ASC, "name"),
            "analysed", Sort.by(Sort.Direction.DESC, "lastAnalyzedAt"));

    private static final int MAX_PAGE_SIZE = 100;

    @Transactional(readOnly = true)
    public ProcessPageDto listProcesses(
            CurrentUser user, int page, int size, ProcessStatus status, String search, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, SORTS.getOrDefault(sort, SORTS.get("recent")));

        // Lowercased and wildcarded here so the query itself stays a plain `like`, and so a user
        // typing % or _ searches for those characters rather than for everything.
        String normalisedSearch = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";

        Page<ProcessSummaryDto> result =
                processRepository.findSummaryPage(user.id(), status, normalisedSearch, pageable);

        return new ProcessPageDto(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasPrevious(),
                result.hasNext(),
                visibleStats(user));
    }

    /**
     * Totals across everything this user can see — their own work plus the shared samples —
     * independent of the current page and filter, so the headline numbers do not move while
     * searching. Scoped to the caller: one user's counts must never include another's work.
     */
    private ProcessPageDto.Stats visibleStats(CurrentUser user) {
        return new ProcessPageDto.Stats(
                processRepository.countVisibleTo(user.id()),
                processRepository.countVisibleToByStatus(user.id(), ProcessStatus.ANALYZED),
                opportunityRepository.countVisibleTo(user.id()),
                futureActivityRepository.countVisibleTo(user.id()));
    }

    @Transactional
    public ProcessDetailDto create(CurrentUser user, CreateProcessRequest request) {
        BusinessProcess process = new BusinessProcess();
        process.setOwner(userRepository.getReferenceById(user.id()));
        process.setName(request.name().trim());
        process.setIndustry(request.industry().trim());
        process.setDescription(request.description().trim());
        process.setStatus(ProcessStatus.CURRENT_ONLY);
        process.setOrigin(ProcessOrigin.USER);
        BusinessProcess saved = processRepository.save(process);

        saveActivities(saved, request.activities());

        log.info("Created process '{}' ({}) for {} with {} activities",
                saved.getName(), saved.getId(), user.email(), request.activities().size());
        return getDetail(user, saved.getId());
    }

    /**
     * Replaces a process definition. Because the current-state activities are the pipeline's input,
     * changing them makes any previously generated future state stale — so it is cleared rather
     * than left to look current.
     */
    @Transactional
    public ProcessDetailDto update(CurrentUser user, UUID processId, UpdateProcessRequest request) {
        accessService.requireWritable(processId, user);

        // Clear derived state first. These are bulk deletes that detach the persistence context,
        // so the process entity is loaded afterwards rather than before.
        interventionRepository.deleteByProcessId(processId);
        futureActivityRepository.deleteByProcessId(processId);
        opportunityRepository.deleteByProcessId(processId);
        problemRepository.deleteByProcessIdAndSource(processId, ProblemSource.AI_GENERATED);
        activityRepository.deleteAll(activityRepository.findByProcessIdOrderBySequenceOrderAsc(processId));
        activityRepository.flush();

        BusinessProcess process = processRepository.findById(processId)
                .orElseThrow(() -> ResourceNotFoundException.of("Process", processId));
        process.setName(request.name().trim());
        process.setIndustry(request.industry().trim());
        process.setDescription(request.description().trim());
        process.setStatus(ProcessStatus.CURRENT_ONLY);
        process.setLastAnalyzedAt(null);
        processRepository.save(process);

        saveActivities(process, request.activities());

        log.info("Updated process '{}' ({}); previous analysis cleared", process.getName(), processId);
        return getDetail(user, processId);
    }

    @Transactional
    public void delete(CurrentUser user, UUID processId) {
        BusinessProcess process = accessService.requireWritable(processId, user);
        // Every child table declares ON DELETE CASCADE, so one delete is enough and cannot orphan rows.
        processRepository.delete(process);
        log.info("Deleted process '{}' ({})", process.getName(), processId);
    }

    @Transactional(readOnly = true)
    public ProcessDetailDto getDetail(CurrentUser user, UUID processId) {
        BusinessProcess process = accessService.requireReadable(processId, user);

        List<Activity> activities =
                activityRepository.findWithRelationsByProcessIdOrderBySequenceOrderAsc(processId);
        List<Problem> problems = problemRepository.findByProcessIdOrderByCreatedAtAsc(processId);
        List<AiOpportunity> opportunities = opportunityRepository.findByProcessIdOrderByDisplayOrderAsc(processId);
        List<FutureActivity> futureActivities =
                futureActivityRepository.findByProcessIdOrderBySequenceOrderAsc(processId);
        List<AiIntervention> interventions = interventionRepository.findByProcessIdOrderByCreatedAtAsc(processId);

        // Reviews, impact figures, risks, plan and scorecard. One extra read rather than five more
        // endpoints: the interface renders them as one document.
        AnalysisInsightService.Insights insights = insightService.forProcess(processId);

        // The latest run of any status, so a failure stays visible in the UI. Its scorecard is
        // attached only when it belongs to that run — a failed re-run must not inherit the score of
        // the successful one before it.
        AnalysisRunSummaryDto latestRun = analysisRunRepository
                .findFirstByProcessIdOrderByStartedAtDesc(processId)
                .map(run -> mapper.toDto(run,
                        insights.scorecard() != null && run.getId().equals(insights.scorecard().analysisRunId())
                                ? insights.scorecard()
                                : null))
                .orElse(null);
        // ...but the Evidence tab must describe the analysis that is actually stored, which is the
        // last one that succeeded — otherwise a failed re-run would relabel sources that never
        // informed the rows on screen.
        List<RetrievedSnippetDto> evidence = analysisRunRepository
                .findFirstByProcessIdAndStatusOrderByStartedAtDesc(processId, AnalysisRunStatus.SUCCEEDED)
                .map(run -> mapper.toDto(run).retrievedSnippets())
                .orElse(List.of());

        Map<UUID, List<Problem>> problemsByActivity = mapper.problemsByActivity(problems);
        Map<UUID, List<AiIntervention>> interventionsByFuture = mapper.interventionsByFutureActivity(interventions);

        return new ProcessDetailDto(
                mapper.toSummary(process, activities.size(), futureActivities.size(), opportunities.size()),
                activities.stream()
                        .map(activity -> mapper.toDto(
                                activity, problemsByActivity.getOrDefault(activity.getId(), List.of())))
                        .toList(),
                problems.stream().map(mapper::toDto).toList(),
                opportunities.stream()
                        .map(opportunity -> mapper.toDto(
                                opportunity,
                                insights.reviewsByOpportunity().get(opportunity.getId()),
                                insights.impactsByOpportunity().get(opportunity.getId())))
                        .toList(),
                futureActivities.stream()
                        .map(futureActivity -> mapper.toDto(
                                futureActivity, interventionsByFuture.getOrDefault(futureActivity.getId(), List.of())))
                        .toList(),
                interventions.stream().map(mapper::toDto).toList(),
                evidence,
                latestRun,
                insights.impacts(),
                insights.risks(),
                insights.roadmap(),
                insights.scorecard(),
                insights.research());
    }

    /**
     * The three-column CURRENT → TRANSITION → FUTURE view, plus roll-up counters. Every number
     * here is computed from stored rows, which is the whole point: the future process is data.
     */
    @Transactional(readOnly = true)
    public ComparisonDto getComparison(CurrentUser user, UUID processId) {
        ProcessDetailDto detail = getDetail(user, processId);

        Set<String> roles = new LinkedHashSet<>();
        Set<String> systems = new LinkedHashSet<>();
        for (ActivityDto activity : detail.activities()) {
            roles.addAll(activity.roles());
            systems.addAll(activity.systems());
        }

        ComparisonDto.Summary summary = new ComparisonDto.Summary(
                detail.activities().size(),
                detail.futureActivities().size(),
                detail.problems().size(),
                detail.opportunities().size(),
                detail.interventions().size(),
                detail.evidence().size(),
                countBy(detail.problems(), problem -> problem.severity().name()),
                countBy(detail.opportunities(), opportunity -> opportunity.automationPotential().name()),
                countBy(detail.futureActivities(), activity -> activity.responsibilityType().name()),
                countBy(detail.interventions(), intervention -> intervention.interventionType().name()));

        return new ComparisonDto(
                detail.process(),
                new ComparisonDto.CurrentState(
                        detail.activities(),
                        detail.problems(),
                        List.copyOf(roles),
                        List.copyOf(systems)),
                new ComparisonDto.Transition(detail.opportunities(), detail.evidence()),
                new ComparisonDto.FutureState(detail.futureActivities(), detail.interventions()),
                summary,
                detail.latestRun());
    }

    private void saveActivities(BusinessProcess process, List<CreateProcessRequest.ActivityInput> inputs) {
        List<Activity> activities = new ArrayList<>(inputs.size());
        int sequence = 1;
        for (CreateProcessRequest.ActivityInput input : inputs) {
            Activity activity = new Activity();
            activity.setProcess(process);
            activity.setName(input.name().trim());
            activity.setSequenceOrder(sequence++);
            activity.setDescription(input.description() == null || input.description().isBlank()
                    ? null
                    : input.description().trim());
            activity.getRoles().addAll(resolveRoles(input.roles()));
            activity.getSystems().addAll(resolveSystems(input.systems()));
            activities.add(activity);
        }
        activityRepository.saveAll(activities);
    }

    /**
     * Roles and systems are a shared lookup, so an unseen name is created once and reused
     * thereafter. Matching is case-insensitive to stop "LMS" and "lms" becoming two rows.
     */
    private List<Role> resolveRoles(List<String> names) {
        if (names == null) {
            return List.of();
        }
        List<Role> resolved = new ArrayList<>();
        for (String raw : distinctTrimmed(names)) {
            Role role = roleRepository.findByNameIgnoreCase(raw)
                    .orElseGet(() -> roleRepository.save(new Role(raw)));
            resolved.add(role);
        }
        return resolved;
    }

    private List<SystemTool> resolveSystems(List<String> names) {
        if (names == null) {
            return List.of();
        }
        List<SystemTool> resolved = new ArrayList<>();
        for (String raw : distinctTrimmed(names)) {
            SystemTool system = systemToolRepository.findByNameIgnoreCase(raw)
                    .orElseGet(() -> systemToolRepository.save(new SystemTool(raw, "Unclassified")));
            resolved.add(system);
        }
        return resolved;
    }

    private List<String> distinctTrimmed(List<String> values) {
        Map<String, String> byLowercase = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            byLowercase.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return List.copyOf(byLowercase.values());
    }

    private <T> Map<String, Integer> countBy(List<T> items, Function<T, String> classifier) {
        Map<String, Integer> counts = new TreeMap<>();
        for (T item : items) {
            counts.merge(classifier.apply(item), 1, Integer::sum);
        }
        return counts;
    }
}
