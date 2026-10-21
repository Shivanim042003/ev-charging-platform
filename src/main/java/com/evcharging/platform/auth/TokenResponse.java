package com.evcharging.platform.auth;

public record TokenResponse(String token, String tokenType, long expiresInSeconds, String username) {
}