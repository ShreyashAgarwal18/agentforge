package com.agentforge.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

	List<ChatSession> findByTenantIdAndUserIdOrderByLastActiveAtDesc(UUID tenantId, UUID userId);

	Optional<ChatSession> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

}
