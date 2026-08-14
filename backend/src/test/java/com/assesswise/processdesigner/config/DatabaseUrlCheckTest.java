package com.assesswise.processdesigner.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

/**
 * The guard around the deployment mistake that actually happened: pasting a hosted provider's
 * libpq connection string, credentials and all, into DATABASE_URL.
 */
class DatabaseUrlCheckTest {

    private final DatabaseUrlCheck check = new DatabaseUrlCheck();

    private void run(String url) {
        MockEnvironment environment = new MockEnvironment();
        if (url != null) {
            environment.setProperty("spring.datasource.url", url);
        }
        check.onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(), new SpringApplication(), new String[0], environment));
    }

    @Test
    @DisplayName("rejects credentials embedded in the URL, and prints the corrected one")
    void rejectsEmbeddedCredentials() {
        assertThatThrownBy(() -> run(
                "jdbc:postgresql://neondb_owner:secret-pw@ep-x-pooler.aws.neon.tech/neondb?sslmode=require"))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(thrown -> {
                    String message = thrown.getMessage();
                    // The fix is spelled out, not merely described.
                    assertThat(message).contains(
                            "use     : jdbc:postgresql://ep-x-pooler.aws.neon.tech/neondb?sslmode=require");
                    assertThat(message).contains("DATABASE_USERNAME=neondb_owner");
                    // ...and the password itself is never echoed back into the logs.
                    assertThat(message).doesNotContain("secret-pw");
                });
    }

    @Test
    @DisplayName("rejects a libpq URL that was never given the jdbc: prefix")
    void rejectsMissingJdbcPrefix() {
        assertThatThrownBy(() -> run("postgresql://ep-x.aws.neon.tech/neondb?sslmode=require"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("use     : jdbc:postgresql://ep-x.aws.neon.tech/neondb?sslmode=require");
    }

    @Test
    @DisplayName("accepts the URLs that actually work, including Neon's extra query parameters")
    void acceptsValidUrls() {
        assertThatCode(() -> {
            run("jdbc:postgresql://ep-x-pooler.aws.neon.tech/neondb?sslmode=require");
            // channel_binding appears in every current Neon connection string and is harmless:
            // the driver connects with it present. Verified against a live Neon database.
            run("jdbc:postgresql://ep-x-pooler.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
            run("jdbc:postgresql://localhost:5432/future_designer");
            run("jdbc:postgresql://127.0.0.1:55432/future_designer");
            run(null);
        }).doesNotThrowAnyException();
    }
}
