package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.RoadmapItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapItemRepository extends JpaRepository<RoadmapItem, UUID> {

    List<RoadmapItem> findByProcessIdOrderByWaveAscDisplayOrderAsc(UUID processId);

    void deleteByProcessId(UUID processId);
}
