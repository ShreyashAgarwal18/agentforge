package com.agentforge.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	private String title;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_active_at", nullable = false)
	private Instant lastActiveAt;

	protected ChatSession() {
	}

	public ChatSession(UUID tenantId, UUID userId, String title) {
		this.tenantId = tenantId;
		this.userId = userId;
		this.title = title;
		Instant now = Instant.now();
		this.createdAt = now;
		this.lastActiveAt = now;
	}

	public void markActive() {
		this.lastActiveAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getTitle() {
		return title;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastActiveAt() {
		return lastActiveAt;
	}

}
