package com.assesswise.processdesigner;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.domain.QueryIntent;
import com.assesswise.processdesigner.service.research.ClaimExtractor;
import com.assesswise.processdesigner.service.research.ContentExtractor;
import com.assesswise.processdesigner.service.research.PageFetcher;
import com.assesswise.processdesigner.service.research.QuoteVerifier;
import com.assesswise.processdesigner.service.research.ResearchQuerySpec;
import com.assesswise.processdesigner.service.research.SearchConnector;
import com.assesswise.processdesigner.service.research.SearchHit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Talks to the real internet.
 *
 * <p>Every other test in this suite is hermetic, and should be. This one exists because the research
 * layer's dependencies are eleven third-party services whose behaviour is not covered by any
 * contract with this project: an endpoint moves, a search engine starts refusing server-side
 * requests, an API changes a field name. A mocked test of a connector proves only that the mock
 * still matches what the connector was written against, which is precisely the thing that goes
 * stale.
 *
 * <p>So this asserts the things that would actually break: that some connector still answers, that
 * a page can still be read, and — most importantly — that a quote taken from real page text still
 * verifies against it. It is skipped unless {@code RESEARCH_LIVE_TESTS=true} is set, so a clean
 * checkout and CI stay offline and deterministic. Run it before a demo:
 *
 * <pre>
 *   RESEARCH_LIVE_TESTS=true GROQ_API_KEY=... ./mvnw test -Dtest=LiveResearchSmokeTest
 * </pre>
 */
@SpringBootTest(properties = {"app.ai.provider=groq", "app.ai.fallback-providers=gemini"})
@ActiveProfiles("live")
@EnabledIfEnvironmentVariable(named = "RESEARCH_LIVE_TESTS", matches = "true")
class LiveResearchSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(LiveResearchSmokeTest.class);

    private static io.zonky.test.db.postgres.embedded.EmbeddedPostgres postgres;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> instance().getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    private static synchronized io.zonky.test.db.postgres.embedded.EmbeddedPostgres instance() {
        if (postgres == null) {
            try {
                postgres = io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder().start();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException("Could not start the embedded PostgreSQL server", e);
            }
        }
        return postgres;
    }

    @Autowired
    private List<SearchConnector> connectors;

    @Autowired
    private PageFetcher pageFetcher;

    @Autowired
    private ContentExtractor contentExtractor;

    @Autowired
    private QuoteVerifier quoteVerifier;

    @Autowired
    private ClaimExtractor claimExtractor;

    @Test
    @DisplayName("at least half the keyless connectors still answer a real query")
    void connectorsAnswer() {
        ResearchQuerySpec query = ResearchQuerySpec.template(
                "automated essay scoring inter-rater reliability", QueryIntent.BENCHMARK);

        List<String> answered = new ArrayList<>();
        List<String> silent = new ArrayList<>();

        for (SearchConnector connector : connectors) {
            if (!connector.isEnabled() || !connector.supports(query.intent())) {
                continue;
            }
            List<SearchHit> hits = connector.search(query, 5);
            if (hits.isEmpty()) {
                silent.add(connector.id());
                continue;
            }
            answered.add("%s(%d)".formatted(connector.id(), hits.size()));
            // Whatever a connector returns must be usable without further cleaning.
            assertThat(hits).allSatisfy(hit -> {
                assertThat(hit.url()).startsWith("http");
                assertThat(hit.title()).isNotBlank();
                assertThat(hit.domain()).isNotBlank().doesNotContain("/");
            });
        }

        log.info("Connectors that answered: {}", answered);
        log.info("Connectors that returned nothing: {}", silent);

        // Not "all": a keyless public API being unavailable is the expected weather, and the design
        // absorbs it. Half failing at once, though, means something has genuinely changed.
        assertThat(answered)
                .describedAs("connectors answering (silent: %s)", silent)
                .hasSizeGreaterThanOrEqualTo(Math.max(1, (answered.size() + silent.size()) / 2));
    }

    @Test
    @DisplayName("a real page can be fetched, read, and a quote from it verifies")
    void fetchReadAndVerify() {
        PageFetcher.FetchResult fetch = pageFetcher.fetch("https://en.wikipedia.org/wiki/Electronic_assessment");

        assertThat(fetch.hasText())
                .describedAs("fetch outcome was %s (%s)", fetch.status(), fetch.note())
                .isTrue();
        String text = fetch.content().text();
        log.info("Read {} characters via {}", text.length(), fetch.method());
        assertThat(text).hasSizeGreaterThan(1000);

        // Take a real sentence out of the middle of the page and confirm the verifier finds it.
        // This is the guarantee the whole evidence model rests on.
        int start = text.indexOf(' ', text.length() / 2) + 1;
        String realQuote = text.substring(start, Math.min(text.length(), start + 160));
        QuoteVerifier.Verification found = quoteVerifier.verify(realQuote, text);
        assertThat(found.verified()).describedAs("a quote copied from the page must verify").isTrue();
        assertThat(found.startOffset()).isNotNull();

        // And that a plausible sentence the page never contained does not verify. A verifier that
        // says yes to everything would be worse than no verifier, because it would look like one.
        QuoteVerifier.Verification invented = quoteVerifier.verify(
                "Electronic assessment platforms were first standardised by the International "
                        + "Assessment Bureau in 1987 following a series of postal examinations.",
                text);
        assertThat(invented.verified()).describedAs("an invented quote must not verify").isFalse();
    }

    @Test
    @DisplayName("claim extraction returns quotes that are actually in the source")
    @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
    void claimsAreGrounded() {
        PageFetcher.FetchResult fetch = pageFetcher.fetch("https://en.wikipedia.org/wiki/Electronic_assessment");
        assertThat(fetch.hasText()).isTrue();

        ClaimExtractor.Extraction extraction = claimExtractor.extract(
                new ClaimExtractor.SourceContext(
                        "Electronic assessment", "Wikipedia", "ENCYCLOPEDIA",
                        "https://en.wikipedia.org/wiki/Electronic_assessment",
                        fetch.content().text()),
                "Redesigning online examination delivery around AI: what is done today and what fails.",
                4);

        log.info("Extracted {} claims ({} model calls, model {})",
                extraction.claims().size(), extraction.modelCalls(), extraction.model());
        extraction.claims().forEach(claim -> log.info("  [{}] {} | quote {} ({})",
                claim.claimType(), claim.claimText(),
                claim.isVerified() ? "VERIFIED" : "UNVERIFIED", claim.verification().method()));

        assertThat(extraction.claims()).isNotEmpty();
        // The model is allowed to miss; it is not allowed to be believed when it does. Most quotes
        // from a clean encyclopaedic page should verify, and any that do not must be marked.
        long verified = extraction.claims().stream().filter(ClaimExtractor.ExtractedClaim::isVerified).count();
        assertThat(verified).describedAs("at least one quote should verify against the source").isPositive();
        assertThat(extraction.claims())
                .allSatisfy(claim -> assertThat(claim.verification()).isNotNull());
    }
}
