package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.AnalysisScorecard;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisScorecardRepository extends JpaRepository<AnalysisScorecard, UUID> {

    Optional<AnalysisScorecard> findFirstByProcessIdOrderByCreatedAtDesc(UUID processId);
}
