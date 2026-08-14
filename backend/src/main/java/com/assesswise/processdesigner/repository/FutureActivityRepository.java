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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FutureActivity fa where fa.process.id = :processId")
    int deleteByProcessId(@Param("processId") UUID processId);
}
