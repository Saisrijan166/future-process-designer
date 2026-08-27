package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.AnalysisRun;
import com.assesswise.processdesigner.domain.AnalysisRunStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, UUID> {

    @EntityGraph(attributePaths = {"retrievedSnippets", "retrievedSnippets.knowledgeSnippet"})
    Optional<AnalysisRun> findFirstByProcessIdOrderByStartedAtDesc(UUID processId);

    /** The run whose output is currently stored — used to label the Evidence tab honestly. */
    @EntityGraph(attributePaths = {"retrievedSnippets", "retrievedSnippets.knowledgeSnippet"})
    Optional<AnalysisRun> findFirstByProcessIdAndStatusOrderByStartedAtDesc(UUID processId, AnalysisRunStatus status);

    List<AnalysisRun> findByProcessIdOrderByStartedAtDesc(UUID processId, Pageable pageable);

    /**
     * Which processes are being analysed right now.
     *
     * <p>One small indexed query for the whole dashboard, rather than a join bolted onto the
     * listing's grouped projection where it would risk the paging counts.
     */
    @Query("select distinct r.process.id from AnalysisRun r where r.status = :status")
    List<UUID> findProcessIdsByStatus(@Param("status") AnalysisRunStatus status);
}
