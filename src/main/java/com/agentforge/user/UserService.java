package com.agentforge.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.agentforge.auth.TenantContext;
import com.agentforge.common.DuplicateResourceException;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TenantContext tenantContext;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, TenantContext tenantContext) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tenantContext = tenantContext;
	}

	public UserResponse createUser(CreateUserRequest request) {
		String email = request.email().toLowerCase();
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateResourceException("Email already in use: " + email);
		}

		User user = new User(tenantContext.tenantId(), email, passwordEncoder.encode(request.password()),
				Role.END_USER);
		userRepository.save(user);
		return UserResponse.from(user);
	}

}
