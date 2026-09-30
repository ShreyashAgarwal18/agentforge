package com.agentforge.common;

// Mapped to HTTP 403 in GlobalExceptionHandler
public class TenantAccessDeniedException extends RuntimeException {

	public TenantAccessDeniedException(String message) {
		super(message);
	}

}
