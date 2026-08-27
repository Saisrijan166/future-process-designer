package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.ClaimRelation;
import com.assesswise.processdesigner.domain.ClaimRelationType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRelationRepository extends JpaRepository<ClaimRelation, UUID> {

    @Query("select relation from ClaimRelation relation "
            + "where relation.claimA.researchRun.id = :researchRunId order by relation.similarity desc")
    List<ClaimRelation> findByRun(@Param("researchRunId") UUID researchRunId);

    @Query("select count(relation) from ClaimRelation relation "
            + "where relation.claimA.researchRun.id = :researchRunId and relation.relationType = :type")
    long countByRunAndType(@Param("researchRunId") UUID researchRunId, @Param("type") ClaimRelationType type);
}
