package com.agentforge.auth;

import java.time.Instant;

public record LoginResponse(String accessToken, Instant expiresAt) {
}
