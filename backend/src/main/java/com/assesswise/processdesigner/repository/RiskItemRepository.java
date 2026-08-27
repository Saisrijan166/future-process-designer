package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.RiskItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskItemRepository extends JpaRepository<RiskItem, UUID> {

    List<RiskItem> findByProcessIdOrderByDisplayOrderAsc(UUID processId);

    @Query("select distinct risk from RiskItem risk left join fetch risk.citedClaims "
            + "where risk.process.id = :processId order by risk.displayOrder asc")
    List<RiskItem> findWithClaimsByProcessId(@Param("processId") UUID processId);

    void deleteByProcessId(UUID processId);
}
