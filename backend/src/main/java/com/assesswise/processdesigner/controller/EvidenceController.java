package com.assesswise.processdesigner.controller;

import com.assesswise.processdesigner.dto.KnowledgeSnippetDto;
import com.assesswise.processdesigner.mapper.DomainMapper;
import com.assesswise.processdesigner.repository.KnowledgeSnippetRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The curated research corpus that grounds every analysis. */
@RestController
@RequestMapping("/api/knowledge-snippets")
@Tag(name = "Evidence", description = "Curated, cited research snippets used as grounding context")
public class EvidenceController {

    private final KnowledgeSnippetRepository snippetRepository;
    private final DomainMapper mapper;

    public EvidenceController(KnowledgeSnippetRepository snippetRepository, DomainMapper mapper) {
        this.snippetRepository = snippetRepository;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List every curated knowledge snippet with its real, checkable source URL")
    @Transactional(readOnly = true)
    public List<KnowledgeSnippetDto> list() {
        return snippetRepository.findAllByOrderByTitleAsc().stream().map(mapper::toDto).toList();
    }
}
