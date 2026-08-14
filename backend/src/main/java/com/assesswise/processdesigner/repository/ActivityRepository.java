package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.Activity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    List<Activity> findByProcessIdOrderBySequenceOrderAsc(UUID processId);

    /** Loads activities with their roles and systems in one round trip (both are Sets, so no bag fetch issue). */
    @EntityGraph(attributePaths = {"roles", "systems"})
    List<Activity> findWithRelationsByProcessIdOrderBySequenceOrderAsc(UUID processId);
}
