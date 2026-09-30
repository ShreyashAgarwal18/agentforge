package com.agentforge.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank @Email String email,
		// max 72: BCrypt silently ignores bytes beyond 72
		@NotBlank @Size(min = 8, max = 72) String password) {
}
