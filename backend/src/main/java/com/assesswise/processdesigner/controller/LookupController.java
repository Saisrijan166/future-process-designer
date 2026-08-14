package com.assesswise.processdesigner.controller;

import com.assesswise.processdesigner.dto.RoleDto;
import com.assesswise.processdesigner.dto.SystemToolDto;
import com.assesswise.processdesigner.mapper.DomainMapper;
import com.assesswise.processdesigner.repository.RoleRepository;
import com.assesswise.processdesigner.repository.SystemToolRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shared lookups, used by the new-process form to suggest roles and systems already in use. */
@RestController
@RequestMapping("/api")
@Tag(name = "Lookups", description = "Reference data shared across processes")
public class LookupController {

    private final RoleRepository roleRepository;
    private final SystemToolRepository systemToolRepository;
    private final DomainMapper mapper;

    public LookupController(
            RoleRepository roleRepository, SystemToolRepository systemToolRepository, DomainMapper mapper) {
        this.roleRepository = roleRepository;
        this.systemToolRepository = systemToolRepository;
        this.mapper = mapper;
    }

    @GetMapping("/roles")
    @Operation(summary = "All known roles")
    @Transactional(readOnly = true)
    public List<RoleDto> roles() {
        return roleRepository.findAllByOrderByNameAsc().stream().map(mapper::toDto).toList();
    }

    @GetMapping("/systems")
    @Operation(summary = "All known systems and tools")
    @Transactional(readOnly = true)
    public List<SystemToolDto> systems() {
        return systemToolRepository.findAllByOrderByNameAsc().stream().map(mapper::toDto).toList();
    }
}
