package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.ResearchSource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResearchSourceRepository extends JpaRepository<ResearchSource, UUID> {

    List<ResearchSource> findByResearchRunIdOrderByDisplayOrderAsc(UUID researchRunId);

    @Query("select count(distinct source.domain) from ResearchSource source "
            + "where source.researchRun.id = :researchRunId")
    long countDistinctDomains(@Param("researchRunId") UUID researchRunId);
}
