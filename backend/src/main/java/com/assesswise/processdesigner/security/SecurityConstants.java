package com.assesswise.processdesigner.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

/** Shared between the token encoder, the decoder and the issuer, so they cannot disagree. */
public final class SecurityConstants {

    /** HMAC-SHA256: symmetric, which is right when the same service both signs and verifies. */
    public static final MacAlgorithm SIGNATURE_ALGORITHM = MacAlgorithm.HS256;

    /** HS256 requires a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    private SecurityConstants() {}
}
