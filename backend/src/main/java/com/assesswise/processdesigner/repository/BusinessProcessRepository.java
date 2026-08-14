package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.ProcessStatus;
import com.assesswise.processdesigner.dto.ProcessSummaryDto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessProcessRepository extends JpaRepository<BusinessProcess, UUID> {

    boolean existsByNameIgnoreCase(String name);

    long countByStatus(ProcessStatus status);

    @EntityGraph(attributePaths = {"activities"})
    Optional<BusinessProcess> findWithActivitiesById(UUID id);

    /**
     * One page of dashboard rows, with the counts the UI needs computed in SQL rather than by
     * walking lazy collections.
     *
     * <p>The count query is supplied explicitly: Spring Data cannot derive a correct one from a
     * {@code GROUP BY} projection — it would count the grouped rows rather than the processes.
     *
     * <p>{@code status} and {@code search} are nullable, and each is bypassed when null. Search
     * arrives already lowercased and wildcarded, with {@code !} declared as the escape character —
     * so a user typing {@code %} searches for a percent sign rather than for everything.
     */
    @Query(value = """
            select new com.assesswise.processdesigner.dto.ProcessSummaryDto(
                p.id, p.name, p.industry, p.description, p.status, p.origin,
                count(distinct a.id), count(distinct fa.id), count(distinct op.id),
                p.createdAt, p.lastAnalyzedAt)
            from BusinessProcess p
            left join Activity a on a.process = p
            left join FutureActivity fa on fa.process = p
            left join AiOpportunity op on op.process = p
            where (:status is null or p.status = :status)
              and (:search is null
                   or lower(p.name) like :search escape '!'
                   or lower(p.industry) like :search escape '!'
                   or lower(p.description) like :search escape '!')
            group by p.id, p.name, p.industry, p.description, p.status, p.origin,
                     p.createdAt, p.lastAnalyzedAt
            """,
            countQuery = """
            select count(p)
            from BusinessProcess p
            where (:status is null or p.status = :status)
              and (:search is null
                   or lower(p.name) like :search escape '!'
                   or lower(p.industry) like :search escape '!'
                   or lower(p.description) like :search escape '!')
            """)
    Page<ProcessSummaryDto> findSummaryPage(
            @Param("status") ProcessStatus status,
            @Param("search") String search,
            Pageable pageable);
}
