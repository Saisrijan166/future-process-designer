package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.WebDocument;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebDocumentRepository extends JpaRepository<WebDocument, UUID> {

    Optional<WebDocument> findByUrlHash(String urlHash);

    /**
     * Expired rows are deleted rather than refreshed in place: a page whose content changed should
     * be re-read and re-quoted, not silently kept with quotes that no longer occur in it.
     */
    @Modifying
    @Query("delete from WebDocument document where document.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
