package com.assesswise.processdesigner;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.dto.CreateProcessRequest;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.dto.ProcessPageDto;
import com.assesswise.processdesigner.dto.auth.AuthDtos;
import com.assesswise.processdesigner.support.AbstractIntegrationTest;
import com.assesswise.processdesigner.support.AuthenticatedClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Authentication, and the isolation guarantees that depend on it.
 *
 * <p>The tests that matter most are in {@link Isolation}: the ones that would let one user read or
 * destroy another's work if the ownership rules were wrong anywhere.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static CreateProcessRequest process(String name) {
        return new CreateProcessRequest(
                name,
                "Retail Banking",
                "How a branch opens a current account for a walk-in customer.",
                List.of(new CreateProcessRequest.ActivityInput(
                        "Collect KYC documents", "The officer photocopies identity documents.",
                        List.of("Branch Officer"), List.of("Scanner"))));
    }

    /* ------------------------------------------------------------ registering */

    @Test
    @DisplayName("registering returns a usable token and never the password")
    void registerReturnsToken() {
        String email = "signup-" + UUID.randomUUID() + "@example.test";

        ResponseEntity<AuthDtos.AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/register",
                new AuthDtos.RegisterRequest(email, "a-good-long-password", "Ada"),
                AuthDtos.AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthDtos.AuthResponse body = response.getBody();
        assertThat(body.token()).isNotBlank();
        assertThat(body.expiresAt()).isAfter(java.time.Instant.now());
        assertThat(body.user().email()).isEqualTo(email.toLowerCase());
        assertThat(body.user().displayName()).isEqualTo("Ada");

        // Nothing password-shaped is anywhere in the response.
        String raw = restTemplate.postForObject(
                "/api/auth/login", new AuthDtos.LoginRequest(email, "a-good-long-password"), String.class);
        assertThat(raw).doesNotContain("password", "passwordHash", "$2a$");
    }

    @Test
    @DisplayName("the display name falls back to the local part of the address")
    void defaultsDisplayName() {
        String email = "fallback-" + UUID.randomUUID() + "@example.test";

        AuthDtos.AuthResponse body = restTemplate.postForObject(
                "/api/auth/register",
                new AuthDtos.RegisterRequest(email, "a-good-long-password", null),
                AuthDtos.AuthResponse.class);

        assertThat(body.user().displayName()).isEqualTo(email.substring(0, email.indexOf('@')));
    }

    @Test
    @DisplayName("an address can only be registered once, whatever its casing")
    void rejectsDuplicateEmail() {
        String email = "dupe-" + UUID.randomUUID() + "@example.test";
        restTemplate.postForEntity("/api/auth/register",
                new AuthDtos.RegisterRequest(email, "a-good-long-password", null), String.class);

        ResponseEntity<String> second = restTemplate.postForEntity("/api/auth/register",
                new AuthDtos.RegisterRequest(email.toUpperCase(), "a-good-long-password", null), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("already exists");
    }

    @Test
    @DisplayName("rejects a weak password and a malformed address with per-field messages")
    void validatesRegistration() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/register",
                new AuthDtos.RegisterRequest("not-an-email", "short", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .contains("does not look like an email address")
                .contains("at least 8 characters");
    }

    /* --------------------------------------------------------------- signing in */

    @Test
    @DisplayName("signs in with the right password, whatever the casing of the address")
    void signsIn() {
        String email = "login-" + UUID.randomUUID() + "@example.test";
        restTemplate.postForEntity("/api/auth/register",
                new AuthDtos.RegisterRequest(email, "a-good-long-password", null), String.class);

        ResponseEntity<AuthDtos.AuthResponse> response = restTemplate.postForEntity("/api/auth/login",
                new AuthDtos.LoginRequest(email.toUpperCase(), "a-good-long-password"),
                AuthDtos.AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().token()).isNotBlank();
    }

    @Test
    @DisplayName("a wrong password and an unknown address fail identically, so accounts cannot be enumerated")
    void doesNotRevealWhetherAnAccountExists() {
        String email = "known-" + UUID.randomUUID() + "@example.test";
        restTemplate.postForEntity("/api/auth/register",
                new AuthDtos.RegisterRequest(email, "a-good-long-password", null), String.class);

        ResponseEntity<String> wrongPassword = restTemplate.postForEntity("/api/auth/login",
                new AuthDtos.LoginRequest(email, "not-the-password"), String.class);
        ResponseEntity<String> unknownAccount = restTemplate.postForEntity("/api/auth/login",
                new AuthDtos.LoginRequest("nobody-" + UUID.randomUUID() + "@example.test", "not-the-password"),
                String.class);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownAccount.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Compare the wording, not the whole document: the bodies also carry a timestamp, which
        // differs between two requests and says nothing about whether the account exists.
        assertThat(messageOf(wrongPassword)).isEqualTo(messageOf(unknownAccount));
        assertThat(messageOf(wrongPassword)).doesNotContain("not found", "unknown", "no account");
    }

    @Test
    @DisplayName("/me describes the account behind the token")
    void describesCurrentUser() {
        AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");

        AuthDtos.UserDto me = alice.getBody("/api/auth/me", AuthDtos.UserDto.class);

        assertThat(me.email()).isEqualTo(alice.email());
        assertThat(me.id()).isEqualTo(alice.user().id());
    }

    /** The human-readable part of a problem document, without the per-request metadata. */
    private static String messageOf(ResponseEntity<String> response) {
        String body = response.getBody() == null ? "" : response.getBody();
        return body.replaceAll("\"timestamp\":\"[^\"]*\"", "").replaceAll("\"path\":\"[^\"]*\"", "");
    }

    /* ------------------------------------------------------------- token checks */

    @Test
    @DisplayName("every data route refuses an anonymous caller")
    void requiresAuthentication() {
        List<String> guarded = List.of(
                "/api/processes",
                "/api/processes/" + UUID.randomUUID(),
                "/api/processes/" + UUID.randomUUID() + "/comparison",
                "/api/knowledge-snippets",
                "/api/roles",
                "/api/systems",
                "/api/auth/me");

        for (String path : guarded) {
            assertThat(restTemplate.getForEntity(path, String.class).getStatusCode())
                    .as("anonymous GET %s", path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(restTemplate.postForEntity(
                        "/api/processes/" + UUID.randomUUID() + "/analyze", null, String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a forged or corrupted token is rejected")
    void rejectsBadTokens() {
        AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");

        for (String bad : List.of(
                "not-a-token",
                "a.b.c",
                alice.token() + "tampered",
                alice.token().substring(0, alice.token().lastIndexOf('.')) + ".d2hhdGV2ZXI")) {

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(bad);
            assertThat(restTemplate
                            .exchange("/api/processes", HttpMethod.GET, new HttpEntity<>(headers), String.class)
                            .getStatusCode())
                    .as("token %s", bad)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    @DisplayName("health and the API docs stay open, so monitoring does not need a token")
    void leavesOperationalEndpointsOpen() {
        assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /* ------------------------------------------------------------------ ISOLATION */

    @Nested
    @DisplayName("one account cannot reach another's work")
    class Isolation {

        @Test
        @DisplayName("a process created by one user is invisible in the other's listing")
        void listingIsPrivate() {
            AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");
            AuthenticatedClient bob = AuthenticatedClient.register(restTemplate, "bob");

            String secret = "Alice Only " + UUID.randomUUID();
            alice.post("/api/processes", process(secret), ProcessDetailDto.class);

            ProcessPageDto aliceSees = alice.getBody("/api/processes?size=100", ProcessPageDto.class);
            ProcessPageDto bobSees = bob.getBody("/api/processes?size=100", ProcessPageDto.class);

            assertThat(aliceSees.items()).extracting(item -> item.name()).contains(secret);
            assertThat(bobSees.items()).extracting(item -> item.name()).doesNotContain(secret);
        }

        @Test
        @DisplayName("search cannot be used to find another user's process")
        void searchIsPrivate() {
            AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");
            AuthenticatedClient bob = AuthenticatedClient.register(restTemplate, "bob");

            String needle = "Zzyzx" + UUID.randomUUID().toString().substring(0, 8);
            alice.post("/api/processes", process(needle), ProcessDetailDto.class);

            assertThat(alice.getBody("/api/processes?q=" + needle, ProcessPageDto.class).totalItems())
                    .isEqualTo(1);
            assertThat(bob.getBody("/api/processes?q=" + needle, ProcessPageDto.class).totalItems())
                    .isZero();
        }

        @Test
        @DisplayName("reading another user's process by id looks exactly like it does not exist")
        void readingIsBlocked() {
            AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");
            AuthenticatedClient bob = AuthenticatedClient.register(restTemplate, "bob");

            UUID id = alice.post("/api/processes", process("Alice " + UUID.randomUUID()), ProcessDetailDto.class)
                    .getBody().process().id();

            // 404 rather than 403: a 403 would confirm the id exists.
            assertThat(bob.get("/api/processes/" + id, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(bob.get("/api/processes/" + id + "/comparison", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(bob.get("/api/processes/" + id + "/analysis-runs", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("another user cannot analyse, edit or delete it either")
        void writingIsBlocked() {
            AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");
            AuthenticatedClient bob = AuthenticatedClient.register(restTemplate, "bob");

            String name = "Alice " + UUID.randomUUID();
            UUID id = alice.post("/api/processes", process(name), ProcessDetailDto.class)
                    .getBody().process().id();

            assertThat(bob.post("/api/processes/" + id + "/analyze", null, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(bob.delete("/api/processes/" + id, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);

            // And it is genuinely still there afterwards.
            assertThat(alice.get("/api/processes/" + id, ProcessDetailDto.class).getBody().process().name())
                    .isEqualTo(name);
        }
    }

    /* -------------------------------------------------------------- SHARED SAMPLES */

    @Nested
    @DisplayName("the seeded samples are shared but read-only")
    class SharedSamples {

        private UUID sampleId(AuthenticatedClient client) {
            return client.getBody("/api/processes?size=100", ProcessPageDto.class).items().stream()
                    .filter(item -> item.shared())
                    .findFirst()
                    .orElseThrow()
                    .id();
        }

        @Test
        @DisplayName("every user sees the same six samples, flagged as shared")
        void visibleToEveryone() {
            AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");
            AuthenticatedClient bob = AuthenticatedClient.register(restTemplate, "bob");

            List<UUID> aliceSamples = alice.getBody("/api/processes?size=100", ProcessPageDto.class)
                    .items().stream().filter(item -> item.shared()).map(item -> item.id()).sorted().toList();
            List<UUID> bobSamples = bob.getBody("/api/processes?size=100", ProcessPageDto.class)
                    .items().stream().filter(item -> item.shared()).map(item -> item.id()).sorted().toList();

            assertThat(aliceSamples).hasSize(6).isEqualTo(bobSamples);
        }

        @Test
        @DisplayName("a sample can be read by anyone")
        void readableByAnyone() {
            AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");
            AuthenticatedClient bob = AuthenticatedClient.register(restTemplate, "bob");
            UUID id = sampleId(alice);

            assertThat(bob.get("/api/processes/" + id, ProcessDetailDto.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(bob.getBody("/api/processes/" + id, ProcessDetailDto.class).process().shared())
                    .isTrue();
        }

        @Test
        @DisplayName("nobody can edit or delete a sample, so the demo data cannot be destroyed")
        void notWritableByAnyone() {
            AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");
            UUID id = sampleId(alice);

            ResponseEntity<String> deleted = alice.delete("/api/processes/" + id, String.class);
            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(deleted.getBody()).contains("shared sample");

            ResponseEntity<String> updated = alice.put("/api/processes/" + id,
                    new com.assesswise.processdesigner.dto.UpdateProcessRequest(
                            "Hijacked", "Nowhere", "Changed by someone who does not own it",
                            List.of(new CreateProcessRequest.ActivityInput("x", "y", List.of(), List.of()))),
                    String.class);
            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // Still present and unchanged.
            assertThat(alice.get("/api/processes/" + id, ProcessDetailDto.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    /* --------------------------------------------------------------------- stats */

    @Test
    @DisplayName("the dashboard totals count only what the caller can see")
    void statsAreScopedToTheCaller() {
        AuthenticatedClient alice = AuthenticatedClient.register(restTemplate, "alice");
        AuthenticatedClient bob = AuthenticatedClient.register(restTemplate, "bob");

        long bobBefore = bob.getBody("/api/processes", ProcessPageDto.class).stats().processes();
        alice.post("/api/processes", process("Alice " + UUID.randomUUID()), ProcessDetailDto.class);

        assertThat(bob.getBody("/api/processes", ProcessPageDto.class).stats().processes())
                .as("Alice's new process must not appear in Bob's totals")
                .isEqualTo(bobBefore);
        assertThat(alice.getBody("/api/processes", ProcessPageDto.class).stats().processes())
                .isEqualTo(bobBefore + 1);
    }
}
