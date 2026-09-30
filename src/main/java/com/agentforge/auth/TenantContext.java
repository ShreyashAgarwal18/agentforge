package com.agentforge.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.agentforge.common.TenantAccessDeniedException;
import com.agentforge.user.Role;

@Component
public class TenantContext {

	public UUID userId() {
		return UUID.fromString(currentJwt().getSubject());
	}

	public UUID tenantId() {
		String tenantId = currentJwt().getClaimAsString("tenantId");
		if (tenantId == null) {
			throw new TenantAccessDeniedException("Current token has no tenantId claim");
		}
		return UUID.fromString(tenantId);
	}

	public Role role() {
		return Role.valueOf(currentJwt().getClaimAsString("role"));
	}

	public boolean isPlatformAdmin() {
		return role() == Role.PLATFORM_ADMIN;
	}

	private Jwt currentJwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new TenantAccessDeniedException("No authenticated JWT in the current security context");
		}
		return jwt;
	}

}
