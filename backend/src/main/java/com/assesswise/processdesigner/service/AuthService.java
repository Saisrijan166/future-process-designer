package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.domain.AppUser;
import com.assesswise.processdesigner.dto.auth.AuthDtos;
import com.assesswise.processdesigner.exception.EmailAlreadyRegisteredException;
import com.assesswise.processdesigner.exception.InvalidCredentialsException;
import com.assesswise.processdesigner.repository.AppUserRepository;
import com.assesswise.processdesigner.security.CurrentUser;
import com.assesswise.processdesigner.security.JwtService;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and sign-in. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = normalise(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException("An account already exists for " + email + ".");
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(displayNameFor(request.displayName(), email));
        user.setLastLoginAt(Instant.now());
        AppUser saved = users.save(user);

        log.info("Registered account {} ({})", saved.getEmail(), saved.getId());
        return respond(saved);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        // The same message and the same work either way: revealing that an address is unknown
        // would let anyone enumerate registered accounts, and returning early would let them time it.
        AppUser user = users.findByEmail(normalise(request.email())).orElse(null);
        String storedHash = user == null ? DUMMY_HASH : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), storedHash);

        if (user == null || !matches) {
            log.info("Failed sign-in attempt for {}", normalise(request.email()));
            throw new InvalidCredentialsException("That email and password do not match an account.");
        }

        user.setLastLoginAt(Instant.now());
        return respond(users.save(user));
    }

    /**
     * A real BCrypt hash of an arbitrary value, so an unknown address costs the same time to
     * reject as a known one with a wrong password.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Transactional(readOnly = true)
    public AuthDtos.UserDto describe(CurrentUser current) {
        return users.findById(current.id())
                .map(this::toDto)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "This session belongs to an account that no longer exists. Sign in again."));
    }

    private AuthDtos.AuthResponse respond(AppUser user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthDtos.AuthResponse(token.value(), token.expiresAt(), toDto(user));
    }

    private AuthDtos.UserDto toDto(AppUser user) {
        return new AuthDtos.UserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }

    private String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String displayNameFor(String requested, String email) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
