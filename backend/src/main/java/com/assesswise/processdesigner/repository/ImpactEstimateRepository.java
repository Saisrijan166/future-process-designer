package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.ImpactEstimate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImpactEstimateRepository extends JpaRepository<ImpactEstimate, UUID> {

    List<ImpactEstimate> findByProcessIdOrderByDisplayOrderAsc(UUID processId);

    void deleteByProcessId(UUID processId);

    @Query("select coalesce(sum(estimate.costSavedPerMonthInr), 0) from ImpactEstimate estimate "
            + "where estimate.process.id = :processId")
    double totalMonthlySaving(@Param("processId") UUID processId);
}
