package com.agentforge.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.agentforge.common.InvalidCredentialsException;
import com.agentforge.tenant.Tenant;
import com.agentforge.tenant.TenantRepository;
import com.agentforge.user.User;
import com.agentforge.user.UserRepository;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final TenantRepository tenantRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, TenantRepository tenantRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.tenantRepository = tenantRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		// Same encoder as real hashes, so its BCrypt cost - and therefore its timing - always matches
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-safety");
	}

	public LoginResponse login(LoginRequest request) {
		String email = request.email().toLowerCase();
		Optional<User> userLookup = userRepository.findByEmail(email);

		if (userLookup.isEmpty()) {
			passwordEncoder.matches(request.password(), dummyPasswordHash);
			throw new InvalidCredentialsException("Invalid email or password");
		}

		User user = userLookup.get();
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException("Invalid email or password");
		}

		if (user.getTenantId() != null && !isTenantActive(user.getTenantId())) {
			throw new InvalidCredentialsException("Invalid email or password");
		}

		Jwt jwt = jwtService.issueToken(user.getId(), user.getRole(), user.getTenantId());
		return new LoginResponse(jwt.getTokenValue(), jwt.getExpiresAt());
	}

	private boolean isTenantActive(UUID tenantId) {
		Optional<Tenant> tenant = tenantRepository.findById(tenantId);
		return tenant.isPresent() && "ACTIVE".equals(tenant.get().getStatus());
	}

}
