package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.ClaimRelation;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.domain.ResearchQuery;
import com.assesswise.processdesigner.domain.ResearchRun;
import com.assesswise.processdesigner.domain.ResearchRunStatus;
import com.assesswise.processdesigner.domain.ResearchSource;
import com.assesswise.processdesigner.repository.ClaimRelationRepository;
import com.assesswise.processdesigner.repository.EvidenceClaimRepository;
import com.assesswise.processdesigner.repository.ResearchQueryRepository;
import com.assesswise.processdesigner.repository.ResearchRunRepository;
import com.assesswise.processdesigner.repository.ResearchSourceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database side of a research run, kept separate from the orchestration.
 *
 * <p>The separation is not tidiness, it is correctness. A research run spends most of its wall clock
 * inside HTTP calls to eleven third parties and inside model requests that may sit waiting on a
 * token bucket — a minute or more in total. Holding a database transaction open across that would
 * pin one of the five connections the free Postgres tier allows, for the entire run.
 *
 * <p>So the orchestrator runs with no transaction at all and calls in here at each checkpoint. Each
 * method below opens its own short transaction, which also means a run that dies halfway has still
 * durably recorded the queries it planned and the sources it found, and the interface can show them.
 */
@Service
public class ResearchPersistence {

    private final ResearchRunRepository runRepository;
    private final ResearchQueryRepository queryRepository;
    private final ResearchSourceRepository sourceRepository;
    private final EvidenceClaimRepository claimRepository;
    private final ClaimRelationRepository relationRepository;

    public ResearchPersistence(
            ResearchRunRepository runRepository,
            ResearchQueryRepository queryRepository,
            ResearchSourceRepository sourceRepository,
            EvidenceClaimRepository claimRepository,
            ClaimRelationRepository relationRepository) {
        this.runRepository = runRepository;
        this.queryRepository = queryRepository;
        this.sourceRepository = sourceRepository;
        this.claimRepository = claimRepository;
        this.relationRepository = relationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResearchRun startRun(BusinessProcess process, UUID analysisRunId) {
        ResearchRun run = new ResearchRun();
        run.setProcess(process);
        run.setAnalysisRunId(analysisRunId);
        run.setStatus(ResearchRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        return runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ResearchQuery> saveQueries(UUID runId, List<ResearchQuerySpec> specs) {
        ResearchRun run = runRepository.getReferenceById(runId);
        List<ResearchQuery> saved = new java.util.ArrayList<>(specs.size());
        int order = 0;
        for (ResearchQuerySpec spec : specs) {
            ResearchQuery query = new ResearchQuery();
            query.setResearchRun(run);
            query.setQueryText(truncate(spec.text(), 500));
            query.setIntent(spec.intent());
            query.setOrigin(spec.origin());
            query.setDisplayOrder(order++);
            saved.add(queryRepository.save(query));
        }
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateQueryStats(UUID queryId, int hitCount, long durationMs) {
        queryRepository.findById(queryId).ifPresent(query -> {
            query.setHitCount(hitCount);
            query.setDurationMs(durationMs);
            queryRepository.save(query);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResearchSource saveSource(ResearchSource source) {
        return sourceRepository.save(source);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ResearchSource> saveSources(List<ResearchSource> sources) {
        return sourceRepository.saveAll(sources);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<EvidenceClaim> saveClaims(List<EvidenceClaim> claims) {
        return claimRepository.saveAll(claims);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRelations(List<ClaimRelation> relations) {
        relationRepository.saveAll(relations);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResearchRun finishRun(
            UUID runId,
            ResearchRunStatus status,
            String connectorsUsed,
            int queryCount,
            int hitCount,
            int documentCount,
            int claimCount,
            int verifiedClaimCount,
            int contradictionCount,
            int distinctDomainCount,
            int cacheHitCount,
            Instant startedAt,
            String notes,
            String errorMessage) {

        ResearchRun run = runRepository.findById(runId).orElseThrow();
        run.setStatus(status);
        run.setConnectorsUsed(truncate(connectorsUsed, 500));
        run.setQueryCount(queryCount);
        run.setHitCount(hitCount);
        run.setDocumentCount(documentCount);
        run.setClaimCount(claimCount);
        run.setVerifiedClaimCount(verifiedClaimCount);
        run.setContradictionCount(contradictionCount);
        run.setDistinctDomainCount(distinctDomainCount);
        run.setCacheHitCount(cacheHitCount);
        run.setNotes(notes);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(Instant.now());
        run.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
        return runRepository.save(run);
    }

    @Transactional(readOnly = true)
    public List<EvidenceClaim> claimsWithSources(UUID researchRunId) {
        return claimRepository.findWithSourcesByRun(researchRunId);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
