package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.FutureActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FutureActivityRepository extends JpaRepository<FutureActivity, UUID> {

    List<FutureActivity> findByProcessIdOrderBySequenceOrderAsc(UUID processId);

    /** Counted for the dashboard, so it must respect the same visibility rule as the listing. */
    @Query("""
            select count(x) from FutureActivity x
            where x.process.owner.id = :ownerId or x.process.owner is null
            """)
    long countVisibleTo(@Param("ownerId") UUID ownerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FutureActivity fa where fa.process.id = :processId")
    int deleteByProcessId(@Param("processId") UUID processId);
}
