package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.ResearchQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchQueryRepository extends JpaRepository<ResearchQuery, UUID> {

    List<ResearchQuery> findByResearchRunIdOrderByDisplayOrderAsc(UUID researchRunId);
}
