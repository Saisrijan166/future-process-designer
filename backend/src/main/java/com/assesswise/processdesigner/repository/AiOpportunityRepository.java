package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.AiOpportunity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiOpportunityRepository extends JpaRepository<AiOpportunity, UUID> {

    @EntityGraph(attributePaths = {"activity", "evidence"})
    List<AiOpportunity> findByProcessIdOrderByDisplayOrderAsc(UUID processId);

    /** Counted for the dashboard, so it must respect the same visibility rule as the listing. */
    @Query("""
            select count(x) from AiOpportunity x
            where x.process.owner.id = :ownerId or x.process.owner is null
            """)
    long countVisibleTo(@Param("ownerId") UUID ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiOpportunity o where o.process.id = :processId")
    int deleteByProcessId(@Param("processId") UUID processId);
}
