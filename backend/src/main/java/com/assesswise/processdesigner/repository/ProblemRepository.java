package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.Problem;
import com.assesswise.processdesigner.domain.ProblemSource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    @EntityGraph(attributePaths = {"activity"})
    List<Problem> findByProcessIdOrderByCreatedAtAsc(UUID processId);

    /** Pain points recorded with the process definition, fed to the model as input context. */
    List<Problem> findByProcessIdAndSourceOrderByCreatedAtAsc(UUID processId, ProblemSource source);

    /**
     * Removes only AI-generated problems. Problems captured with the process definition itself
     * are user data and must survive a re-analysis.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Problem p where p.process.id = :processId and p.source = :source")
    int deleteByProcessIdAndSource(@Param("processId") UUID processId, @Param("source") ProblemSource source);
}
