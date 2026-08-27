package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.ResearchRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchRunRepository extends JpaRepository<ResearchRun, UUID> {

    List<ResearchRun> findByProcessIdOrderByStartedAtDesc(UUID processId);

    Optional<ResearchRun> findFirstByProcessIdOrderByStartedAtDesc(UUID processId);

    Optional<ResearchRun> findByAnalysisRunId(UUID analysisRunId);
}
