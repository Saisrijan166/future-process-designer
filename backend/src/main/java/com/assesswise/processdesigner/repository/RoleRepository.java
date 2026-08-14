package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameIgnoreCase(String name);

    List<Role> findAllByOrderByNameAsc();
}
