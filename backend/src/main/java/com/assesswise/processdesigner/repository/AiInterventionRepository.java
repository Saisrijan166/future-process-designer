package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.AiIntervention;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiInterventionRepository extends JpaRepository<AiIntervention, UUID> {

    @EntityGraph(attributePaths = {"futureActivity", "relatedAiOpportunity"})
    List<AiIntervention> findByProcessIdOrderByCreatedAtAsc(UUID processId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiIntervention i where i.process.id = :processId")
    int deleteByProcessId(@Param("processId") UUID processId);
}
