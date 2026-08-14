package com.assesswise.processdesigner.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.dto.auth.AuthDtos;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A registered account with its bearer token, wrapping {@link TestRestTemplate} so tests read as
 * "alice creates a process" rather than as header plumbing.
 *
 * <p>Each instance registers a fresh account with a unique address, so tests are independent of
 * each other and of whatever the database already holds.
 */
public final class AuthenticatedClient {

    private final TestRestTemplate restTemplate;
    private final String token;
    private final AuthDtos.UserDto user;

    private AuthenticatedClient(TestRestTemplate restTemplate, String token, AuthDtos.UserDto user) {
        this.restTemplate = restTemplate;
        this.token = token;
        this.user = user;
    }

    /** Registers a brand-new account and returns a client already holding its token. */
    public static AuthenticatedClient register(TestRestTemplate restTemplate, String label) {
        String email = label + "-" + UUID.randomUUID() + "@example.test";
        ResponseEntity<AuthDtos.AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/register",
                new AuthDtos.RegisterRequest(email, "correct-horse-battery", label),
                AuthDtos.AuthResponse.class);

        assertThat(response.getStatusCode())
                .as("registering %s", email)
                .isEqualTo(HttpStatus.CREATED);
        AuthDtos.AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        return new AuthenticatedClient(restTemplate, body.token(), body.user());
    }

    public AuthDtos.UserDto user() {
        return user;
    }

    public String email() {
        return user.email();
    }

    public String token() {
        return token;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    public <T> ResponseEntity<T> get(String path, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers()), type);
    }

    public <T> ResponseEntity<T> get(String path, ParameterizedTypeReference<T> type) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers()), type);
    }

    public <T> T getBody(String path, Class<T> type) {
        return get(path, type).getBody();
    }

    public <T> ResponseEntity<T> post(String path, Object body, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers()), type);
    }

    public <T> ResponseEntity<T> put(String path, Object body, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers()), type);
    }

    public <T> ResponseEntity<T> delete(String path, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers()), type);
    }
}
