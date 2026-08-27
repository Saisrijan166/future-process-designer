package com.assesswise.processdesigner.service.research.connector;

import com.assesswise.processdesigner.domain.SourceType;
import com.assesswise.processdesigner.service.ai.AiCompletion;
import com.assesswise.processdesigner.service.ai.AiGateway;
import com.assesswise.processdesigner.service.ai.AiRequest;
import com.assesswise.processdesigner.service.ai.AiTask;
import com.assesswise.processdesigner.service.research.ResearchQuerySpec;
import com.assesswise.processdesigner.service.research.SearchConnector;
import com.assesswise.processdesigner.service.research.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Groq's agentic model as a search connector.
 *
 * <p>{@code groq/compound} runs its own web searches server-side and reports what it read back in
 * {@code executed_tools} — title, URL and a substantial excerpt per page. That output is treated
 * here exactly like a search result from any other connector: parsed into hits, stored as sources,
 * and subject to the same quote verification. Nothing the model *says* is trusted; only the page
 * text it hands over is used, and only where a quote can be located in it.
 *
 * <p>Two things make this the most valuable connector of the eleven despite being the most
 * expensive. It reaches pages the keyless connectors cannot — its infrastructure is not the one
 * being blocked — and it arrives with the body text already extracted, so those sources need no
 * fetch, no reader fallback and no politeness delay.
 *
 * <p>The cost is real and bounded accordingly: 250 requests a day on the free tier against 1,000
 * for the general models, so it is called once per run with the whole research brief rather than
 * once per query. Its 70,000 tokens-a-minute allowance is nearly nine times the general models',
 * which is why one large call is the right shape.
 */
@Component
public class GroqAgentConnector implements SearchConnector {

    private static final Logger log = LoggerFactory.getLogger(GroqAgentConnector.class);

    /** Matches the {@code Title:/URL:/Content:} blocks the search tool emits. */
    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "Title:\\s*(?<title>[^\\n]{1,400})\\s*\\nURL:\\s*(?<url>\\S{5,900})\\s*\\n"
                    + "Content:\\s*(?<content>.*?)(?=\\nTitle:\\s|\\z)",
            Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
            You are a research assistant gathering evidence for a business-process redesign.
            Search the web and read the most authoritative sources you can find. Prefer statutes, \
            regulator guidance, published standards, peer-reviewed studies and named organisations \
            over vendor marketing. Answer in at most 120 words: the sources you read matter, not \
            your summary of them.""";

    private final AiGateway aiGateway;

    public GroqAgentConnector(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
    }

    @Override
    public String id() {
        return "groq-agent";
    }

    @Override
    public String displayName() {
        return "Groq agentic search";
    }

    @Override
    public SourceType defaultSourceType() {
        return SourceType.GENERAL_WEB;
    }

    @Override
    public boolean isEnabled() {
        return aiGateway.isConfigured();
    }

    @Override
    public List<SearchHit> search(ResearchQuerySpec query, int limit) {
        String prompt = """
                Research this question and cite what you read. Search the web before answering.

                QUESTION: %s

                Report the two or three most useful findings in at most 120 words.
                """.formatted(query.text());

        try {
            AiCompletion completion = aiGateway.complete(
                    AiTask.RESEARCH_AGENT,
                    new AiRequest(prompt, SYSTEM_PROMPT, "research-agent", false,
                            null, null, null, null, null, true));

            List<SearchHit> hits = new ArrayList<>();
            for (AiCompletion.ExecutedTool tool : completion.executedTools()) {
                if (!"search".equalsIgnoreCase(tool.type()) && !"visit_website".equalsIgnoreCase(tool.type())) {
                    continue;
                }
                hits.addAll(parseToolOutput(tool.output(), hits.size(), limit - hits.size()));
                if (hits.size() >= limit) {
                    break;
                }
            }
            if (hits.isEmpty()) {
                log.info("{} answered '{}' without reporting any sources it read", id(), query.text());
            }
            return hits;

        } catch (RuntimeException e) {
            // Its request budget is a quarter of the general models'. Running out is expected, not
            // exceptional, and the other ten connectors carry the run.
            log.info("{} unavailable for '{}': {}", id(), query.text(), e.getMessage());
            return List.of();
        }
    }

    private List<SearchHit> parseToolOutput(String output, int startingRank, int limit) {
        if (output == null || output.isBlank() || limit <= 0) {
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();
        Matcher matcher = RESULT_BLOCK.matcher(output);
        int rank = startingRank;
        while (matcher.find() && hits.size() < limit) {
            String url = matcher.group("url").trim();
            String title = matcher.group("title").trim();
            String content = matcher.group("content").trim();
            if (url.isBlank() || title.isBlank()) {
                continue;
            }
            String domain = SearchHit.domainOf(url);
            hits.add(new SearchHit(
                    id(),
                    url,
                    title,
                    content.length() > 600 ? content.substring(0, 600) : content,
                    domain,
                    null,
                    BingWebSearchConnector.classify(domain, url),
                    rank++,
                    // The excerpt is the page text as the tool returned it. Long enough to quote
                    // from, which is why these sources skip fetching entirely.
                    content.length() > 400 ? content : null));
        }
        return hits;
    }
}
