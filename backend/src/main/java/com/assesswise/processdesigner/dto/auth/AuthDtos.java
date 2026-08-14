package com.assesswise.processdesigner.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response shapes for authentication. Grouped, because each is three lines. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "email is required")
            @Email(message = "that does not look like an email address")
            @Size(max = 320)
            String email,

            @NotBlank(message = "password is required")
            @Size(min = 8, max = 100, message = "password must be at least 8 characters")
            String password,

            /** Optional; the part of the email before the @ is used when it is absent. */
            @Size(max = 120)
            String displayName) {}

    public record LoginRequest(
            @NotBlank(message = "email is required") String email,
            @NotBlank(message = "password is required") String password) {}

    /** The signed token plus the account it belongs to. The password hash never appears here. */
    public record AuthResponse(String token, Instant expiresAt, UserDto user) {}

    public record UserDto(UUID id, String email, String displayName, Instant createdAt) {}
}
