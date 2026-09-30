package com.agentforge.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
		@NotBlank String name,
		@NotBlank String slug,
		String systemPrompt,
		String tone,
		@NotBlank @Email String adminEmail,
		// max 72: BCrypt silently ignores bytes beyond 72
		@NotBlank @Size(min = 8, max = 72) String adminPassword) {
}
