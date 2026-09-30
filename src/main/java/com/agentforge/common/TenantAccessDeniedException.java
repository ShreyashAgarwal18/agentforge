package com.agentforge.common;

// TODO: map to HTTP 403 in the global error handler (not built yet)
public class TenantAccessDeniedException extends RuntimeException {

	public TenantAccessDeniedException(String message) {
		super(message);
	}

}
