package com.agentforge.auth;

public record LoginResponse(String accessToken, long expiresInSeconds) {
}
