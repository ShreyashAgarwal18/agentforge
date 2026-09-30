package com.agentforge.common;

// Mapped to HTTP 409 in GlobalExceptionHandler
public class DuplicateResourceException extends RuntimeException {

	public DuplicateResourceException(String message) {
		super(message);
	}

}
