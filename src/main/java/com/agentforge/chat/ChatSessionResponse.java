package com.agentforge.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(UUID id, String title, Instant createdAt, Instant lastActiveAt) {

	public static ChatSessionResponse from(ChatSession session) {
		return new ChatSessionResponse(session.getId(), session.getTitle(), session.getCreatedAt(),
				session.getLastActiveAt());
	}

}
