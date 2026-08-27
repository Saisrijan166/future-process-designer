package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.domain.Problem;
import com.assesswise.processdesigner.domain.QueryIntent;
import com.assesswise.processdesigner.service.PromptTemplateRenderer;
import com.assesswise.processdesigner.service.ai.AiCompletion;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiRequest;
import com.assesswise.processdesigner.service.ai.AiTask;
import com.assesswise.processdesigner.service.ai.StructuredJson;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * Decides what to search for.
 *
 * <p>The quality of a research run is set here, before a single request goes out. A process
 * described as "checking answer scripts" will find almost nothing under those words; searched as
 * "automated essay scoring inter-rater reliability" it finds three decades of measurement. So a
 * model is asked to translate the brief into the domain's own vocabulary and to cover several
 * distinct angles — what the field does, what fails, what the law requires, what has been measured.
 *
 * <p>There is a deterministic fallback, and it is not a token gesture: if the planning model is out
 * of quota or returns nonsense, {@link #templateQueries} builds a serviceable plan from the process
 * fields alone. Live research then still happens. A pipeline whose research layer collapses because
 * one small model call failed would be a worse design than one with no research layer at all,
 * because it would look fine right up until the demo.
 *
 * <p>The planner also enforces coverage the model tends to skip. Models reliably propose capability
 * and benchmark searches and reliably forget that the work is regulated, so REGULATION and
 * BENCHMARK are added if the returned plan omits them.
 */
@Service
public class ResearchQueryPlanner {

    private static final Logger log = LoggerFactory.getLogger(ResearchQueryPlanner.class);
    private static final String TEMPLATE_PATH = "prompts/plan-research-queries.txt";

    /** Intents a plan should not be missing, because the model routinely leaves them out. */
    private static final List<QueryIntent> REQUIRED_INTENTS =
            List.of(QueryIntent.REGULATION, QueryIntent.BENCHMARK, QueryIntent.RISK);

    private final AiGateway aiGateway;
    private final StructuredJson structuredJson;
    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();
    private final int maxQueries;
    private String template;

    public ResearchQueryPlanner(AiGateway aiGateway, StructuredJson structuredJson, AppProperties properties) {
        this.aiGateway = aiGateway;
        this.structuredJson = structuredJson;
        this.maxQueries = Math.max(2, properties.research().maxQueries());
    }

    @PostConstruct
    void loadTemplate() {
        try {
            template = StreamUtils.copyToString(
                    new ClassPathResource(TEMPLATE_PATH).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + TEMPLATE_PATH, e);
        }
    }

    /**
     * @param plan the queries to run, best first
     * @param prompt what was asked, for the run trace
     * @param completion the model's answer, or null when the fallback was used
     * @param note one line explaining how this plan came about
     */
    public record Plan(List<ResearchQuerySpec> plan, String prompt, AiCompletion completion, String note) {}

    public Plan plan(BusinessProcess process, List<Activity> activities, List<Problem> problems) {
        String prompt = renderPrompt(process, activities, problems);
        try {
            AiCompletion completion = aiGateway.complete(AiTask.QUERY_PLANNING, AiRequest.of(prompt, "query-planning"));
            List<ResearchQuerySpec> parsed = parse(completion.text());
            if (parsed.isEmpty()) {
                log.warn("The planning model returned no usable queries; using the deterministic plan");
                return new Plan(templateQueries(process, activities), prompt, completion,
                        "The planning model returned no usable queries, so a template plan was used");
            }
            List<ResearchQuerySpec> complete = ensureCoverage(parsed, process, activities);
            return new Plan(complete, prompt, completion,
                    complete.size() > parsed.size()
                            ? "Model plan, extended to cover intents it omitted"
                            : "Planned by " + completion.model());

        } catch (RuntimeException e) {
            log.warn("Query planning failed ({}); using the deterministic plan", e.getMessage());
            return new Plan(templateQueries(process, activities), prompt, null,
                    "The planning model was unavailable (" + e.getMessage() + "), so a template plan was used");
        }
    }

    private String renderPrompt(BusinessProcess process, List<Activity> activities, List<Problem> problems) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("name", process.getName());
        context.put("industry", process.getIndustry());
        context.put("description", process.getDescription());
        context.put("today", LocalDate.now());
        context.put("max_queries", maxQueries);

        List<Map<String, Object>> activityRows = new ArrayList<>();
        for (Activity activity : activities) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sequence_order", activity.getSequenceOrder());
            row.put("name", activity.getName());
            row.put("description", activity.getDescription() == null || activity.getDescription().isBlank()
                    ? "no description provided"
                    : activity.getDescription());
            activityRows.add(row);
        }
        context.put("activities", activityRows);

        List<Map<String, Object>> problemRows = new ArrayList<>();
        for (Problem problem : problems) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("severity", problem.getSeverity());
            row.put("description", problem.getDescription());
            problemRows.add(row);
        }
        context.put("known_problems", problemRows);
        return renderer.render(template, context);
    }

    private List<ResearchQuerySpec> parse(String rawResponse) {
        StructuredJson.Result<JsonNode> result = structuredJson.parseTree(rawResponse);
        if (!result.isSuccess()) {
            log.info("Query plan was not parseable: {}", result.error());
            return List.of();
        }
        List<ResearchQuerySpec> queries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (JsonNode node : structuredJson.arrayAt(result.value(), "queries")) {
            String text = clean(node.path("text").asText(""));
            if (text.length() < 6 || text.length() > 240) {
                continue;
            }
            // Two spellings of the same search waste a request each.
            if (!seen.add(text.toLowerCase(Locale.ROOT))) {
                continue;
            }
            QueryIntent intent = parseIntent(node.path("intent").asText(""));
            queries.add(ResearchQuerySpec.model(text, intent));
            if (queries.size() >= maxQueries) {
                break;
            }
        }
        return queries;
    }

    /** Adds the angles the model skipped, up to the query ceiling. */
    private List<ResearchQuerySpec> ensureCoverage(
            List<ResearchQuerySpec> planned, BusinessProcess process, List<Activity> activities) {

        List<ResearchQuerySpec> result = new ArrayList<>(planned);
        Set<QueryIntent> covered = new LinkedHashSet<>();
        planned.forEach(query -> covered.add(query.intent()));

        for (QueryIntent required : REQUIRED_INTENTS) {
            if (result.size() >= maxQueries + REQUIRED_INTENTS.size()) {
                break;
            }
            if (!covered.contains(required)) {
                result.add(ResearchQuerySpec.template(templateFor(required, process, activities), required));
            }
        }
        return result;
    }

    /**
     * The deterministic plan. Uses the process's own words plus a domain-neutral frame per intent —
     * unimaginative next to a model's plan, but it works on any industry, needs no quota, and
     * cannot fail.
     */
    List<ResearchQuerySpec> templateQueries(BusinessProcess process, List<Activity> activities) {
        List<ResearchQuerySpec> queries = new ArrayList<>();
        for (QueryIntent intent : List.of(
                QueryIntent.DOMAIN_BASELINE, QueryIntent.AI_CAPABILITY, QueryIntent.PAIN_POINT,
                QueryIntent.REGULATION, QueryIntent.BENCHMARK, QueryIntent.CASE_STUDY)) {
            queries.add(ResearchQuerySpec.template(templateFor(intent, process, activities), intent));
            if (queries.size() >= maxQueries) {
                break;
            }
        }
        return queries;
    }

    private String templateFor(QueryIntent intent, BusinessProcess process, List<Activity> activities) {
        String subject = clean(process.getName());
        String industry = clean(process.getIndustry());
        String firstActivity = activities.isEmpty() ? subject : clean(activities.getFirst().getName());

        return switch (intent) {
            case DOMAIN_BASELINE -> "%s %s standard operating practice".formatted(industry, subject);
            case PAIN_POINT -> "%s %s common problems delays errors".formatted(industry, subject);
            case AI_CAPABILITY -> "artificial intelligence automation %s %s".formatted(subject, firstActivity);
            case REGULATION -> "%s data protection compliance requirements India AI %s"
                    .formatted(industry, subject);
            case BENCHMARK -> "%s automation accuracy time saving measured results".formatted(subject);
            case VENDOR_LANDSCAPE -> "%s software vendors %s platform comparison".formatted(industry, subject);
            case RISK -> "AI %s risks bias privacy failure %s".formatted(subject, industry);
            case CASE_STUDY -> "%s organisation case study AI implementation %s".formatted(industry, subject);
        };
    }

    private QueryIntent parseIntent(String raw) {
        if (raw == null || raw.isBlank()) {
            return QueryIntent.DOMAIN_BASELINE;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "_");
        for (QueryIntent intent : QueryIntent.values()) {
            if (intent.name().equals(key)) {
                return intent;
            }
        }
        // Models paraphrase enum values; a near miss is better read than discarded.
        return switch (key) {
            case "LAW", "LEGAL", "COMPLIANCE", "REGULATORY" -> QueryIntent.REGULATION;
            case "METRICS", "MEASUREMENT", "EVIDENCE", "STATISTICS" -> QueryIntent.BENCHMARK;
            case "VENDOR", "VENDORS", "MARKET", "TOOLS" -> QueryIntent.VENDOR_LANDSCAPE;
            case "PROBLEM", "PROBLEMS", "PAIN", "PAIN_POINTS" -> QueryIntent.PAIN_POINT;
            case "RISKS", "HARM", "SAFETY" -> QueryIntent.RISK;
            case "CAPABILITY", "TECHNOLOGY", "AI" -> QueryIntent.AI_CAPABILITY;
            case "CASE_STUDIES", "EXAMPLES", "EXAMPLE" -> QueryIntent.CASE_STUDY;
            default -> QueryIntent.DOMAIN_BASELINE;
        };
    }

    /** Search engines do not want punctuation, and quotation marks break several of the APIs. */
    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\"'`\\n\\r]+", " ").replaceAll("\\s+", " ").trim();
    }
}
