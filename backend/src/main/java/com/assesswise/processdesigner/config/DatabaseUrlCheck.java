package com.assesswise.processdesigner.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Turns the single most likely deployment mistake into a sentence instead of a stack trace.
 *
 * <p>Every hosted Postgres — Neon, Supabase, Railway — hands you a libpq URL with the credentials
 * inside it:
 *
 * <pre>postgresql://user:password&#64;host/db?sslmode=require</pre>
 *
 * <p>Prefixing that with {@code jdbc:} looks right and is not: the PostgreSQL JDBC driver expects
 * the credentials as separate properties, reads {@code password@host} as the port, and fails deep
 * inside Hikari with "Driver org.postgresql.Driver claims to not accept jdbcUrl" — forty lines of
 * stack trace that never mention the actual problem.
 *
 * <p>This listener runs before any bean is created, so the message arrives first and on its own.
 */
public class DatabaseUrlCheck implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final String URL_PROPERTY = "spring.datasource.url";

    /** {@code //something:something@host} — credentials embedded in the authority. */
    private static final Pattern EMBEDDED_CREDENTIALS =
            Pattern.compile("^jdbc:postgresql://([^/@\\s]+):([^/@\\s]*)@(.+)$");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        String url = environment.getProperty(URL_PROPERTY);
        if (url == null || url.isBlank()) {
            return;
        }

        Matcher matcher = EMBEDDED_CREDENTIALS.matcher(url.trim());
        if (matcher.matches()) {
            String user = matcher.group(1);
            String remainder = matcher.group(3);
            throw new IllegalStateException("""

                    DATABASE_URL has the username and password inside it, which the PostgreSQL JDBC \
                    driver does not accept.

                      you set : jdbc:postgresql://%s:<password>@%s
                      use     : jdbc:postgresql://%s

                    Move the credentials into their own variables:
                      DATABASE_USERNAME=%s
                      DATABASE_PASSWORD=<the password you removed from the URL>

                    Everything after the host — the database name and the query string, including \
                    sslmode and channel_binding — stays exactly as it is.
                    """.formatted(user, remainder, remainder, user));
        }

        if (!url.startsWith("jdbc:")) {
            throw new IllegalStateException("""

                    DATABASE_URL must be a JDBC URL, so it has to start with "jdbc:".

                      you set : %s
                      use     : jdbc:%s
                    """.formatted(url, url));
        }
    }
}
