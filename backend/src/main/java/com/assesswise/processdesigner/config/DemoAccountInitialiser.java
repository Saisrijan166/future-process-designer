package com.assesswise.processdesigner.config;

import com.assesswise.processdesigner.domain.AppUser;
import com.assesswise.processdesigner.repository.AppUserRepository;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a known account on first start so the app can be opened without registering first.
 *
 * <p>Convenience for a demo, and a liability anywhere else — so it announces itself loudly in the
 * log and can be switched off with {@code AUTH_DEMO_ACCOUNT_ENABLED=false}. It never changes the
 * password of an account that already exists, so it cannot reset a real one.
 */
@Configuration
public class DemoAccountInitialiser {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitialiser.class);

    @Bean
    public ApplicationRunner seedDemoAccount(
            AppProperties properties, AppUserRepository users, PasswordEncoder passwordEncoder) {

        return args -> {
            AppProperties.Auth auth = properties.auth();

            if (auth.usingDefaultSecret()) {
                log.warn("AUTH_JWT_SECRET is still the built-in development value. Anyone who knows it can "
                        + "mint a token for any account — set a real one before deploying. "
                        + "Generate with: openssl rand -base64 48");
            }

            AppProperties.DemoAccount demo = auth.demoAccount();
            if (!demo.enabled()) {
                return;
            }

            String email = demo.email().trim().toLowerCase(Locale.ROOT);
            if (users.existsByEmail(email)) {
                log.info("Demo account {} already exists — leaving its password untouched.", email);
                return;
            }

            createDemoAccount(users, passwordEncoder, email, demo);
            log.warn("Created the demo account {} with a known password. Disable it outside a demo with "
                    + "AUTH_DEMO_ACCOUNT_ENABLED=false.", email);
        };
    }

    @Transactional
    void createDemoAccount(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            String email,
            AppProperties.DemoAccount demo) {

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(demo.password()));
        user.setDisplayName(demo.displayName());
        user.setCreatedAt(Instant.now());
        users.save(user);
    }
}
