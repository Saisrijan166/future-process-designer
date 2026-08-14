package com.assesswise.processdesigner.security;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.domain.AppUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues the signed tokens the API authenticates with.
 *
 * <p>Stateless JWTs rather than a server session, for a specific deployment reason: the frontend is
 * on Vercel and the API on Render, which are different sites. A session cookie would have to be
 * {@code SameSite=None} third-party — increasingly blocked by browsers by default — whereas a
 * bearer token in the Authorization header is unaffected by any of that.
 *
 * <p>The subject is the user's id, not their email, so changing an address later cannot invalidate
 * or misdirect a token.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final AppProperties.Auth config;

    public JwtService(JwtEncoder encoder, AppProperties properties) {
        this.encoder = encoder;
        this.config = properties.auth();
    }

    /** A freshly signed token and the moment it stops being valid. */
    public record IssuedToken(String value, Instant expiresAt) {}

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(config.tokenTtlHours(), ChronoUnit.HOURS);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ai-future-process-designer")
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .build();

        String token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(SecurityConstants.SIGNATURE_ALGORITHM).build(), claims))
                .getTokenValue();

        return new IssuedToken(token, expiry);
    }
}
