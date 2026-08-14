package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.dto.ProcessSummaryDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BusinessProcessRepository extends JpaRepository<BusinessProcess, UUID> {

    boolean existsByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = {"activities"})
    Optional<BusinessProcess> findWithActivitiesById(UUID id);

    /**
     * Dashboard listing with the counts the UI needs, computed in one query rather than
     * N+1 lazy collection loads.
     */
    @Query("""
            select new com.assesswise.processdesigner.dto.ProcessSummaryDto(
                p.id, p.name, p.industry, p.description, p.status, p.origin,
                count(distinct a.id), count(distinct fa.id), count(distinct op.id),
                p.createdAt, p.lastAnalyzedAt)
            from BusinessProcess p
            left join Activity a on a.process = p
            left join FutureActivity fa on fa.process = p
            left join AiOpportunity op on op.process = p
            group by p.id, p.name, p.industry, p.description, p.status, p.origin,
                     p.createdAt, p.lastAnalyzedAt
            order by p.createdAt desc
            """)
    List<ProcessSummaryDto> findAllSummaries();
}
