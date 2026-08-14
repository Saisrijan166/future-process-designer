package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.AnalysisRun;
import com.assesswise.processdesigner.domain.AnalysisRunStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, UUID> {

    @EntityGraph(attributePaths = {"retrievedSnippets", "retrievedSnippets.knowledgeSnippet"})
    Optional<AnalysisRun> findFirstByProcessIdOrderByStartedAtDesc(UUID processId);

    /** The run whose output is currently stored — used to label the Evidence tab honestly. */
    @EntityGraph(attributePaths = {"retrievedSnippets", "retrievedSnippets.knowledgeSnippet"})
    Optional<AnalysisRun> findFirstByProcessIdAndStatusOrderByStartedAtDesc(UUID processId, AnalysisRunStatus status);

    List<AnalysisRun> findByProcessIdOrderByStartedAtDesc(UUID processId, Pageable pageable);
}
