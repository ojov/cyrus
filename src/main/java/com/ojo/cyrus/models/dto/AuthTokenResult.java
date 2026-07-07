package com.ojo.cyrus.models.dto;

/**
 * Carries the raw JWT alongside the public-facing response body, from {@code AuthService} up to
 * {@code AuthController} only — the token is set as an httpOnly cookie there and never appears in
 * {@code response}, which is exactly what gets serialized back to the client.
 */
public record AuthTokenResult<T>(String token, T response) {}
