package com.agentforge.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.agentforge.document.DocumentService;
import com.agentforge.tenant.CreateTenantRequest;
import com.agentforge.tenant.TenantRepository;
import com.agentforge.tenant.TenantResponse;
import com.agentforge.tenant.TenantService;
import com.agentforge.user.CreateUserRequest;
import com.agentforge.user.UserService;

// Only runs with --spring.profiles.active=demo - never in tests, never by default
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private final TenantRepository tenantRepository;
	private final TenantService tenantService;
	private final UserService userService;
	private final DocumentService documentService;
	private final String demoAdminPassword;
	private final String demoUserPassword;

	public DemoDataSeeder(TenantRepository tenantRepository, TenantService tenantService, UserService userService,
			DocumentService documentService, @Value("${AGENTFORGE_DEMO_ADMIN_PASSWORD}") String demoAdminPassword,
			@Value("${AGENTFORGE_DEMO_USER_PASSWORD}") String demoUserPassword) {
		this.tenantRepository = tenantRepository;
		this.tenantService = tenantService;
		this.userService = userService;
		this.documentService = documentService;
		this.demoAdminPassword = demoAdminPassword;
		this.demoUserPassword = demoUserPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		// "-demo" suffix: a manually-created tenant can never collide with these slugs,
		// so "slug already exists" always means "this seeder already ran", nothing else
		trySeedTenant("Sunny Side Cake Shop", "sunny-side-cake-shop-demo",
				"You are the friendly assistant for a small cake shop.", "warm and welcoming",
				"admin@sunnyside.demo", "customer@sunnyside.demo", "samples/cake-shop-faq.txt");
		trySeedTenant("Riverside Family Clinic", "riverside-clinic-demo",
				"You are the assistant for a family medical clinic.", "calm and professional",
				"admin@riverside.demo", "patient@riverside.demo", "samples/clinic-faq.txt");
	}

	// One tenant's seeding failure (e.g. a real Gemini quota error during document upload) must
	// not take the other tenant down with it, and must never fail application startup entirely
	private void trySeedTenant(String name, String slug, String systemPrompt, String tone, String adminEmail,
			String userEmail, String sampleResourcePath) {
		try {
			seedTenant(name, slug, systemPrompt, tone, adminEmail, userEmail, sampleResourcePath);
		}
		catch (Exception ex) {
			log.error("Failed to seed demo tenant '{}' (slug: {}) - startup continues regardless", name, slug, ex);
		}
	}

	// Already-exists check on the slug is the whole idempotency guard: if the tenant is there,
	// assume its user and document were seeded alongside it in the same earlier run
	private void seedTenant(String name, String slug, String systemPrompt, String tone, String adminEmail,
			String userEmail, String sampleResourcePath) {
		if (tenantRepository.existsBySlug(slug)) {
			return;
		}

		TenantResponse tenant = tenantService
			.createTenant(new CreateTenantRequest(name, slug, systemPrompt, tone, adminEmail, demoAdminPassword));

		runAsTenantAdmin(tenant.id(), () -> {
			userService.createUser(new CreateUserRequest(userEmail, demoUserPassword));
			Resource resource = new ClassPathResource(sampleResourcePath);
			String filename = sampleResourcePath.substring(sampleResourcePath.lastIndexOf('/') + 1);
			documentService.uploadDocument(filename, resource);
		});
	}

	// TenantService/UserService/DocumentService are real request-time services that read
	// tenantId from TenantContext - fake a short-lived security context to call them at startup
	private void runAsTenantAdmin(UUID tenantId, Runnable action) {
		Instant now = Instant.now();
		Jwt jwt = Jwt.withTokenValue("demo-seed-token")
			.header("alg", "none")
			.issuedAt(now)
			.expiresAt(now.plusSeconds(60))
			.subject(UUID.randomUUID().toString())
			.claim("tenantId", tenantId.toString())
			.claim("role", "TENANT_ADMIN")
			.build();
		var authorities = List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
		try {
			action.run();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

}
