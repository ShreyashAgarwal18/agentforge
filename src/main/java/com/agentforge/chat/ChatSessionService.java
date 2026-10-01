package com.agentforge.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.stereotype.Service;

import com.agentforge.auth.TenantContext;
import com.agentforge.common.SessionNotFoundException;

@Service
public class ChatSessionService {

	private final ChatSessionRepository chatSessionRepository;
	private final ChatMemoryRepository chatMemoryRepository;
	private final TenantContext tenantContext;

	public ChatSessionService(ChatSessionRepository chatSessionRepository, ChatMemoryRepository chatMemoryRepository,
			TenantContext tenantContext) {
		this.chatSessionRepository = chatSessionRepository;
		this.chatMemoryRepository = chatMemoryRepository;
		this.tenantContext = tenantContext;
	}

	public ChatSessionResponse createSession(CreateChatSessionRequest request) {
		ChatSession session = new ChatSession(tenantContext.tenantId(), tenantContext.userId(), request.title());
		chatSessionRepository.save(session);
		return ChatSessionResponse.from(session);
	}

	public List<ChatSessionResponse> listSessions() {
		return chatSessionRepository
			.findByTenantIdAndUserIdOrderByLastActiveAtDesc(tenantContext.tenantId(), tenantContext.userId())
			.stream()
			.map(ChatSessionResponse::from)
			.toList();
	}

	public void deleteSession(UUID sessionId) {
		ChatSession session = findOwnedSession(sessionId);
		chatMemoryRepository.deleteByConversationId(conversationId(session));
		chatSessionRepository.delete(session);
	}

	public void touch(ChatSession session) {
		session.markActive();
		chatSessionRepository.save(session);
	}

	// Package-private: ChatService uses this too, so both enforce the identical ownership check
	ChatSession findOwnedSession(UUID sessionId) {
		return chatSessionRepository
			.findByIdAndTenantIdAndUserId(sessionId, tenantContext.tenantId(), tenantContext.userId())
			.orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));
	}

	public static String conversationId(ChatSession session) {
		return session.getTenantId() + ":" + session.getUserId() + ":" + session.getId();
	}

}
