package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.SystemTool;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemToolRepository extends JpaRepository<SystemTool, UUID> {

    Optional<SystemTool> findByNameIgnoreCase(String name);

    List<SystemTool> findAllByOrderByNameAsc();
}
