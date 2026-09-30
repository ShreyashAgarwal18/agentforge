package com.agentforge.tenant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentforge.common.DuplicateResourceException;
import com.agentforge.user.Role;
import com.agentforge.user.User;
import com.agentforge.user.UserRepository;

@Service
public class TenantService {

	private final TenantRepository tenantRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public TenantService(TenantRepository tenantRepository, UserRepository userRepository,
			PasswordEncoder passwordEncoder) {
		this.tenantRepository = tenantRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public TenantResponse createTenant(CreateTenantRequest request) {
		if (tenantRepository.existsBySlug(request.slug())) {
			throw new DuplicateResourceException("Tenant slug already in use: " + request.slug());
		}

		String adminEmail = request.adminEmail().toLowerCase();
		if (userRepository.existsByEmail(adminEmail)) {
			throw new DuplicateResourceException("Email already in use: " + adminEmail);
		}

		Tenant tenant = new Tenant(request.name(), request.slug(), request.systemPrompt(), request.tone(), 0);
		tenantRepository.save(tenant);

		User admin = new User(tenant.getId(), adminEmail, passwordEncoder.encode(request.adminPassword()),
				Role.TENANT_ADMIN);
		userRepository.save(admin);

		return TenantResponse.from(tenant);
	}

}
