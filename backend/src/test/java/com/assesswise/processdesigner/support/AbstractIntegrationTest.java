package com.assesswise.processdesigner.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for tests that need a real database.
 *
 * <p>Runs against an actual PostgreSQL server rather than an in-memory substitute, started from
 * the {@code embedded-postgres} binaries — no Docker daemon and no locally installed server
 * required, so {@code ./mvnw verify} works on a clean machine and in CI. Using real Postgres
 * matters here because the schema is created by the same Flyway migrations that run in
 * production, and Hibernate is configured to {@code validate} against it: a mapping that drifts
 * from the migration fails the build instead of failing at 3am.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(StubAiProviderConfig.class)
public abstract class AbstractIntegrationTest {

    private static EmbeddedPostgres postgres;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> instance().getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    /** One server for the whole test run; each class reuses it rather than paying start-up again. */
    private static synchronized EmbeddedPostgres instance() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start the embedded PostgreSQL server", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // The JVM is going away anyway.
                }
            }));
        }
        return postgres;
    }
}
