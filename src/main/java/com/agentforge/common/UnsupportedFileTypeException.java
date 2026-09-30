package com.agentforge.common;

// Mapped to HTTP 400 in GlobalExceptionHandler
public class UnsupportedFileTypeException extends RuntimeException {

	public UnsupportedFileTypeException(String message) {
		super(message);
	}

}
