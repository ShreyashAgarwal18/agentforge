package com.agentforge.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlatformAdminSeeder implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String adminEmail;
	private final String adminPassword;

	public PlatformAdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
			@Value("${agentforge.admin.email}") String adminEmail,
			@Value("${AGENTFORGE_ADMIN_PASSWORD:}") String adminPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminEmail = adminEmail.toLowerCase();
		if (!StringUtils.hasText(adminPassword)) {
			throw new IllegalStateException("AGENTFORGE_ADMIN_PASSWORD must be set to seed the platform admin");
		}
		this.adminPassword = adminPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userRepository.existsByRole(Role.PLATFORM_ADMIN)) {
			return;
		}
		User admin = new User(null, adminEmail, passwordEncoder.encode(adminPassword), Role.PLATFORM_ADMIN);
		userRepository.save(admin);
	}

}
