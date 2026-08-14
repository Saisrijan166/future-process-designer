package com.assesswise.processdesigner.security;

import com.assesswise.processdesigner.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Everything under {@code /api} needs a token, except signing up and signing in.
 *
 * <p>Stateless: no session is created, so the API scales and restarts without anyone being logged
 * out mid-demo. Rejections are returned as the same RFC 7807 problem documents the rest of the API
 * uses, because a caller should not have to parse two different error shapes.
 */
@Configuration
public class SecurityConfig {

    private final AppProperties.Auth config;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AppProperties properties, ObjectMapper objectMapper) {
        this.config = properties.auth();
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CORS uses the WebMvcConfigurer mapping already defined for the frontend origin.
                .cors(Customizer.withDefaults())
                // No cookies are used for authentication, so there is no CSRF surface to protect:
                // a bearer token is not attached automatically by the browser.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED, "Not signed in",
                                        "This request needs a valid session. Sign in and try again.",
                                        request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(response, HttpStatus.FORBIDDEN, "Not allowed",
                                        "Your account does not have access to that.",
                                        request.getRequestURI())))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED, "Not signed in",
                                        "This request needs a valid session. Sign in and try again.",
                                        request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(response, HttpStatus.FORBIDDEN, "Not allowed",
                                        "Your account does not have access to that.",
                                        request.getRequestURI())));

        return http.build();
    }

    private void writeProblem(
            HttpServletResponse response, HttpStatus status, String title, String detail, String path)
            throws java.io.IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("path", path);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    /**
     * BCrypt at the default strength (10). Chosen over a faster hash on purpose: password hashing
     * should be slow, and ~50ms per sign-in is invisible to a user and expensive to an attacker.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private SecretKeySpec secretKey() {
        byte[] secret = config.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < SecurityConstants.MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "AUTH_JWT_SECRET must be at least " + SecurityConstants.MIN_SECRET_BYTES
                            + " characters for HS256. Generate one with: openssl rand -base64 48");
        }
        return new SecretKeySpec(secret, SecurityConstants.SIGNATURE_ALGORITHM.getName());
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder
                .withSecretKey(secretKey())
                .macAlgorithm(SecurityConstants.SIGNATURE_ALGORITHM)
                .build();
    }
}
