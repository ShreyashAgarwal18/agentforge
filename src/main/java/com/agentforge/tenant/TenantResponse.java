package com.agentforge.tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
		UUID id,
		String name,
		String slug,
		String systemPrompt,
		String tone,
		long tokenQuota,
		long tokensUsed,
		TenantStatus status,
		Instant createdAt) {

	public static TenantResponse from(Tenant tenant) {
		return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getSystemPrompt(),
				tenant.getTone(), tenant.getTokenQuota(), tenant.getTokensUsed(), tenant.getStatus(),
				tenant.getCreatedAt());
	}

}
