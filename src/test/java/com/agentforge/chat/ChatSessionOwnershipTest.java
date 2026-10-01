package com.agentforge.chat;

import java.util.UUID;

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
import com.agentforge.tenant.CreateTenantRequest;
import com.agentforge.user.CreateUserRequest;

import static org.assertj.core.api.Assertions.assertThat;

// Real end-to-end test, not opt-in - uses Testcontainers, not real Gemini
@Testcontainers
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "GEMINI_API_KEY=dummy-test-key",
				"AGENTFORGE_JWT_SECRET=integration-test-jwt-secret-value-1234567890",
				"AGENTFORGE_ADMIN_PASSWORD=test-admin-password" })
class ChatSessionOwnershipTest {

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
	void anotherUsersSessionReturns404OnDelete() {
		TwoUserSetup setup = setUpTwoUsersAndSessionOwnedByA("diner");

		// Same tenant, different user - must not be able to touch user A's session
		ResponseEntity<String> deleteAttempt = restTemplate.exchange(url("/api/sessions/" + setup.sessionId()),
				HttpMethod.DELETE, authorized(null, setup.userBToken()), String.class);
		assertThat(deleteAttempt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// Proves the ownership check runs before the stream starts: if it ran inside the reactive
	// chain instead, the response would start as 200 with an empty/failed SSE body, not a clean 404
	@Test
	void anotherUsersSessionReturns404ImmediatelyOnStream() {
		TwoUserSetup setup = setUpTwoUsersAndSessionOwnedByA("bistro");

		ResponseEntity<String> streamAttempt = restTemplate.exchange(
				url("/api/sessions/" + setup.sessionId() + "/messages/stream"), HttpMethod.POST,
				authorized(new SendMessageRequest("hello"), setup.userBToken()), String.class);
		assertThat(streamAttempt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private TwoUserSetup setUpTwoUsersAndSessionOwnedByA(String slug) {
		String platformAdminToken = login("admin@agentforge.local", "test-admin-password");

		restTemplate.exchange(url("/api/platform/tenants"), HttpMethod.POST,
				authorized(new CreateTenantRequest(slug, slug, null, null, "admin@" + slug + ".test",
						"tenant-admin-password"), platformAdminToken), Void.class);
		String tenantAdminToken = login("admin@" + slug + ".test", "tenant-admin-password");

		restTemplate.exchange(url("/api/users"), HttpMethod.POST,
				authorized(new CreateUserRequest("user-a@" + slug + ".test", "user-a-password"), tenantAdminToken),
				Void.class);
		restTemplate.exchange(url("/api/users"), HttpMethod.POST,
				authorized(new CreateUserRequest("user-b@" + slug + ".test", "user-b-password"), tenantAdminToken),
				Void.class);

		String userAToken = login("user-a@" + slug + ".test", "user-a-password");
		String userBToken = login("user-b@" + slug + ".test", "user-b-password");

		ResponseEntity<ChatSessionResponse> sessionResponse = restTemplate.exchange(url("/api/sessions"),
				HttpMethod.POST, authorized(new CreateChatSessionRequest("User A's session"), userAToken),
				ChatSessionResponse.class);
		assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		return new TwoUserSetup(sessionResponse.getBody().id(), userAToken, userBToken);
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

	private record TwoUserSetup(UUID sessionId, String userAToken, String userBToken) {
	}

}
