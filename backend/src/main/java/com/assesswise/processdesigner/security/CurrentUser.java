package com.assesswise.processdesigner.security;

import java.util.UUID;

/**
 * The authenticated caller, resolved from the token on each request.
 *
 * <p>A small value type rather than passing {@code Authentication} around, so the service layer
 * depends on "who is asking" rather than on Spring Security.
 */
public record CurrentUser(UUID id, String email, String displayName) {}
