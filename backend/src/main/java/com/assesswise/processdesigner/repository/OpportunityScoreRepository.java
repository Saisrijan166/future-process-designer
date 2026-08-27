package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.OpportunityScore;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpportunityScoreRepository extends JpaRepository<OpportunityScore, UUID> {

    @Query("select score from OpportunityScore score where score.opportunity.process.id = :processId")
    List<OpportunityScore> findByProcessId(@Param("processId") UUID processId);
}
