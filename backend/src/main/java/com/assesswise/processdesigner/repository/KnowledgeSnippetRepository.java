package com.assesswise.processdesigner.repository;

import com.assesswise.processdesigner.domain.KnowledgeSnippet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSnippetRepository extends JpaRepository<KnowledgeSnippet, UUID> {

    List<KnowledgeSnippet> findAllByOrderByTitleAsc();
}
