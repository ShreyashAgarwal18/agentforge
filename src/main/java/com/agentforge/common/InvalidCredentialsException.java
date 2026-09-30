package com.agentforge.common;

// Mapped to HTTP 401 in GlobalExceptionHandler
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException(String message) {
		super(message);
	}

}
