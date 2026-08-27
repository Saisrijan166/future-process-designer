package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.AnalysisStage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisStageRepository extends JpaRepository<AnalysisStage, UUID> {

    List<AnalysisStage> findByAnalysisRunIdOrderByDisplayOrderAsc(UUID analysisRunId);
}
