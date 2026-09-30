package com.agentforge.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
class DocumentUploadAuthorizationTest {

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

	// Real Gemini embedding call fails with the dummy key above, so a successful upload
	// still ends up FAILED internally - this test only checks the HTTP-level authorization
	// and validation outcomes, not whether embedding itself succeeded.
	@Test
	void uploadAuthorizationAndValidation() {
		String platformAdminToken = login("admin@agentforge.local", "test-admin-password");

		CreateTenantRequest createTenant = new CreateTenantRequest("Bakery", "bakery", null, null,
				"tenant-admin@bakery.test", "tenant-admin-password");
		restTemplate.exchange(url("/api/platform/tenants"), HttpMethod.POST,
				authorized(createTenant, platformAdminToken), Void.class);

		String tenantAdminToken = login("tenant-admin@bakery.test", "tenant-admin-password");

		CreateUserRequest createUser = new CreateUserRequest("end-user@bakery.test", "end-user-password");
		restTemplate.exchange(url("/api/users"), HttpMethod.POST, authorized(createUser, tenantAdminToken),
				Void.class);
		String endUserToken = login("end-user@bakery.test", "end-user-password");

		// TENANT_ADMIN upload succeeds (request-level) - 201
		ResponseEntity<DocumentResponse> uploadResponse = restTemplate.exchange(url("/api/documents"),
				HttpMethod.POST, multipartUpload("notes.txt", "hello world", tenantAdminToken),
				DocumentResponse.class);
		assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// END_USER upload is forbidden - 403
		ResponseEntity<String> endUserUpload = restTemplate.exchange(url("/api/documents"), HttpMethod.POST,
				multipartUpload("notes.txt", "hello world", endUserToken), String.class);
		assertThat(endUserUpload.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		// Unsupported file type is rejected - 400
		ResponseEntity<String> badType = restTemplate.exchange(url("/api/documents"), HttpMethod.POST,
				multipartUpload("virus.exe", "not a real document", tenantAdminToken), String.class);
		assertThat(badType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private String login(String email, String password) {
		ResponseEntity<LoginResponse> response = restTemplate.postForEntity(url("/api/auth/login"),
				new LoginRequest(email, password), LoginResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody().accessToken();
	}

	private HttpEntity<MultiValueMap<String, Object>> multipartUpload(String filename, String content, String token) {
		ByteArrayResource fileResource = new ByteArrayResource(content.getBytes()) {
			@Override
			public String getFilename() {
				return filename;
			}
		};

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", fileResource);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.setBearerAuth(token);
		return new HttpEntity<>(body, headers);
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
