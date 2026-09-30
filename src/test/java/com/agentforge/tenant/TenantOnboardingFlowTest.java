package com.agentforge.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.agentforge.auth.LoginRequest;
import com.agentforge.auth.LoginResponse;
import com.agentforge.user.CreateUserRequest;
import com.agentforge.user.UserResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Real end-to-end test, not opt-in (no "IT" suffix) - uses Testcontainers, not real Gemini
@Testcontainers
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "GEMINI_API_KEY=dummy-test-key",
				"AGENTFORGE_JWT_SECRET=integration-test-jwt-secret-value-1234567890",
				"AGENTFORGE_ADMIN_PASSWORD=test-admin-password" })
class TenantOnboardingFlowTest {

	// pgvector/pgvector image needed - our schema init requires the pgvector extension
	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName(
			"mydatabase")
		.withUsername("myuser")
		.withPassword("secret");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void fullOnboardingFlowAndAuthorizationChecks() {
		String platformAdminToken = login("admin@agentforge.local", "test-admin-password");

		CreateTenantRequest createTenant = new CreateTenantRequest("Cake Shop", "cake-shop", null, null,
				"tenant-admin@cakeshop.test", "tenant-admin-password");
		ResponseEntity<TenantResponse> tenantResponse = restTemplate.exchange(url("/api/platform/tenants"),
				HttpMethod.POST, authorized(createTenant, platformAdminToken), TenantResponse.class);
		assertThat(tenantResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// Duplicate slug -> 409
		ResponseEntity<String> duplicateResponse = restTemplate.exchange(url("/api/platform/tenants"),
				HttpMethod.POST, authorized(createTenant, platformAdminToken), String.class);
		assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		// Wrong password -> 401
		ResponseEntity<String> wrongPassword = restTemplate.postForEntity(url("/api/auth/login"),
				new LoginRequest("tenant-admin@cakeshop.test", "wrong-password"), String.class);
		assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		String tenantAdminToken = login("tenant-admin@cakeshop.test", "tenant-admin-password");

		CreateUserRequest createUser = new CreateUserRequest("end-user@cakeshop.test", "end-user-password");
		ResponseEntity<UserResponse> userResponse = restTemplate.exchange(url("/api/users"), HttpMethod.POST,
				authorized(createUser, tenantAdminToken), UserResponse.class);
		assertThat(userResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		String endUserToken = login("end-user@cakeshop.test", "end-user-password");

		// End user cannot create other users -> 403
		CreateUserRequest anotherUser = new CreateUserRequest("someone-else@cakeshop.test", "another-password");
		ResponseEntity<String> forbidden = restTemplate.exchange(url("/api/users"), HttpMethod.POST,
				authorized(anotherUser, endUserToken), String.class);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	private String login(String email, String password) {
		ResponseEntity<LoginResponse> response = restTemplate.postForEntity(url("/api/auth/login"),
				new LoginRequest(email, password), LoginResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody().accessToken();
	}

	private <T> HttpEntity<T> authorized(T body, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return new HttpEntity<>(body, headers);
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

}
