package com.agentforge.tenant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class Tenant {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(name = "system_prompt")
	private String systemPrompt;

	private String tone;

	@Column(name = "token_quota", nullable = false)
	private long tokenQuota;

	@Column(name = "tokens_used", nullable = false)
	private long tokensUsed;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TenantStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Tenant() {
	}

	public Tenant(String name, String slug, String systemPrompt, String tone, long tokenQuota) {
		this.name = name;
		this.slug = slug;
		this.systemPrompt = systemPrompt;
		this.tone = tone;
		this.tokenQuota = tokenQuota;
		this.tokensUsed = 0;
		this.status = TenantStatus.ACTIVE;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public String getSystemPrompt() {
		return systemPrompt;
	}

	public String getTone() {
		return tone;
	}

	public long getTokenQuota() {
		return tokenQuota;
	}

	public long getTokensUsed() {
		return tokensUsed;
	}

	public TenantStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
