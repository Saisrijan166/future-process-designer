package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.Problem;
import com.assesswise.processdesigner.domain.Role;
import com.assesswise.processdesigner.domain.SystemTool;
import com.assesswise.processdesigner.service.KnowledgeRetrievalService.ScoredSnippet;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Turns database rows into the two prompts the pipeline uses. Prompt text itself lives in
 * {@code resources/prompts/} — this class only supplies data, so changing the wording never
 * requires changing Java.
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private static final String ANALYZE_TEMPLATE_PATH = "prompts/analyze-process.txt";
    private static final String REPAIR_TEMPLATE_PATH = "prompts/repair-json.txt";
    /** Trimmed so a rambling failed response cannot blow past the model's input window. */
    private static final int MAX_PREVIOUS_RESPONSE_CHARS = 6000;

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();
    private String analyzeTemplate;
    private String repairTemplate;

    @PostConstruct
    void loadTemplates() {
        analyzeTemplate = readClasspath(ANALYZE_TEMPLATE_PATH);
        repairTemplate = readClasspath(REPAIR_TEMPLATE_PATH);
        log.info("Loaded prompt templates ({} chars analyse, {} chars repair)",
                analyzeTemplate.length(), repairTemplate.length());
    }

    public String buildAnalysisPrompt(
            BusinessProcess process,
            List<Activity> activities,
            List<Problem> knownProblems,
            List<ScoredSnippet> snippets) {

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("name", process.getName());
        context.put("industry", process.getIndustry());
        context.put("description", process.getDescription());
        context.put("activities", activityRows(activities));
        context.put("known_problems", problemRows(knownProblems));
        context.put("snippets", snippetRows(snippets));
        return renderer.render(analyzeTemplate, context);
    }

    public String buildRepairPrompt(String originalPrompt, String previousResponse, List<String> errors) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("errors", errors.stream().map(error -> "- " + error).reduce((a, b) -> a + "\n" + b).orElse("- Invalid JSON"));
        context.put("previous_response", truncate(previousResponse));
        context.put("original_prompt", originalPrompt);
        return renderer.render(repairTemplate, context);
    }

    private List<Map<String, Object>> activityRows(List<Activity> activities) {
        List<Map<String, Object>> rows = new ArrayList<>(activities.size());
        for (Activity activity : activities) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sequence_order", activity.getSequenceOrder());
            row.put("name", activity.getName());
            row.put("description", blankToDash(activity.getDescription()));
            row.put("roles", joinOrNone(activity.getRoles().stream().map(Role::getName).toList()));
            row.put("systems", joinOrNone(activity.getSystems().stream().map(SystemTool::getName).toList()));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> problemRows(List<Problem> problems) {
        List<Map<String, Object>> rows = new ArrayList<>(problems.size());
        for (Problem problem : problems) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("severity", problem.getSeverity());
            row.put("description", problem.getDescription());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> snippetRows(List<ScoredSnippet> snippets) {
        List<Map<String, Object>> rows = new ArrayList<>(snippets.size());
        for (ScoredSnippet scored : snippets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source_type", scored.snippet().getSourceType());
            row.put("title", scored.snippet().getTitle());
            row.put("snippet_text", scored.snippet().getSnippetText());
            row.put("source_url", scored.snippet().getSourceUrl());
            rows.add(row);
        }
        return rows;
    }

    private String joinOrNone(List<String> values) {
        return values.isEmpty() ? "not recorded" : String.join(", ", values);
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "no description provided" : value;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_PREVIOUS_RESPONSE_CHARS
                ? value
                : value.substring(0, MAX_PREVIOUS_RESPONSE_CHARS) + "\n…[truncated]";
    }

    private String readClasspath(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load prompt template " + path, e);
        }
    }
}
