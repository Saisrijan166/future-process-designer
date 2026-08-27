package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.EvidenceClaim;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceClaimRepository extends JpaRepository<EvidenceClaim, UUID> {

    List<EvidenceClaim> findByResearchRunIdOrderByCitationIndexAsc(UUID researchRunId);

    /** Fetches the source with the claim: the UI never shows a quote without its citation. */
    @Query("select claim from EvidenceClaim claim join fetch claim.source "
            + "where claim.researchRun.id = :researchRunId order by claim.citationIndex asc")
    List<EvidenceClaim> findWithSourcesByRun(@Param("researchRunId") UUID researchRunId);

    @Query("select claim from EvidenceClaim claim join fetch claim.source source "
            + "where claim.id in (select cited.id from AiOpportunity opportunity "
            + "join opportunity.citedClaims cited where opportunity.process.id = :processId) "
            + "order by claim.citationIndex asc")
    List<EvidenceClaim> findCitedByProcess(@Param("processId") UUID processId);

    long countByResearchRunIdAndQuoteVerifiedTrue(UUID researchRunId);
}
