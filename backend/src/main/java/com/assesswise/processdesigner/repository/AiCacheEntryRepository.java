package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.AiCacheEntry;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCacheEntryRepository extends JpaRepository<AiCacheEntry, String> {

    @Modifying
    @Query("delete from AiCacheEntry entry where entry.createdAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);

    @Query("select coalesce(sum(entry.hitCount), 0) from AiCacheEntry entry")
    long totalHits();
}
