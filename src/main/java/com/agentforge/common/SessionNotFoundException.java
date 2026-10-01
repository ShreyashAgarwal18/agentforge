package com.agentforge.common;

// Mapped to HTTP 404 in GlobalExceptionHandler
public class SessionNotFoundException extends RuntimeException {

	public SessionNotFoundException(String message) {
		super(message);
	}

}
