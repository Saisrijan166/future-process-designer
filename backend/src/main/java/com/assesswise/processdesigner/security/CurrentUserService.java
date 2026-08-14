package com.assesswise.processdesigner.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Reads the authenticated caller out of the security context. */
@Service
public class CurrentUserService {

    /**
     * The caller, or empty when the request is anonymous.
     *
     * <p>Every {@code /api} route except authentication requires a token, so services can treat
     * {@link #require()} as always succeeding; this variant exists for the few places that must
     * behave differently when signed out.
     */
    public Optional<CurrentUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CurrentUser(
                    UUID.fromString(jwt.getSubject()),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name")));
        } catch (IllegalArgumentException e) {
            // A token whose subject is not a UUID was not issued by this service.
            return Optional.empty();
        }
    }

    public CurrentUser require() {
        return find().orElseThrow(() -> new IllegalStateException(
                "No authenticated user on a request that requires one — check the security configuration."));
    }
}
