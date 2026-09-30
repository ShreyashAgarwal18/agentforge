package com.agentforge.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import com.agentforge.document.DocumentResponse;
import com.agentforge.document.DocumentStatus;
import com.agentforge.tenant.CreateTenantRequest;
import com.agentforge.tenant.TenantResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Real end-to-end test, not opt-in - uses Testcontainers and a fake EmbeddingModel, not real Gemini
@Testcontainers
@AutoConfigureTestRestTemplate
@Import(TenantAwareRetrieverIsolationTest.FakeEmbeddingConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "GEMINI_API_KEY=dummy-test-key",
				"AGENTFORGE_JWT_SECRET=integration-test-jwt-secret-value-1234567890",
				"AGENTFORGE_ADMIN_PASSWORD=test-admin-password" })
class TenantAwareRetrieverIsolationTest {

	private static final String DOCUMENT_TEXT = "The secret bakery recipe uses saffron and honey.";

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

	@Autowired
	private TenantAwareRetriever tenantAwareRetriever;

	@Test
	void tenantIsolationOnSearch() {
		String platformAdminToken = login("admin@agentforge.local", "test-admin-password");

		UUID tenantAId = createTenant(platformAdminToken, "bakery-a", "admin-a@bakery.test",
				"tenant-admin-password").id();
		UUID tenantBId = createTenant(platformAdminToken, "bakery-b", "admin-b@bakery.test",
				"tenant-admin-password").id();

		String tenantAToken = login("admin-a@bakery.test", "tenant-admin-password");

		ResponseEntity<DocumentResponse> uploadResponse = restTemplate.exchange(url("/api/documents"),
				HttpMethod.POST, multipartUpload("recipe.txt", DOCUMENT_TEXT, tenantAToken), DocumentResponse.class);
		assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(uploadResponse.getBody().status()).isEqualTo(DocumentStatus.READY);

		assertThat(tenantAwareRetriever.retrieve(DOCUMENT_TEXT, tenantAId)).isNotEmpty();
		assertThat(tenantAwareRetriever.retrieve(DOCUMENT_TEXT, tenantBId)).isEmpty();
	}

	private TenantResponse createTenant(String platformAdminToken, String slug, String adminEmail,
			String adminPassword) {
		CreateTenantRequest request = new CreateTenantRequest(slug, slug, null, null, adminEmail, adminPassword);
		ResponseEntity<TenantResponse> response = restTemplate.exchange(url("/api/platform/tenants"),
				HttpMethod.POST, authorized(request, platformAdminToken), TenantResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
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

	@TestConfiguration
	static class FakeEmbeddingConfig {

		// Deterministic 768-dim vectors derived from the text, so uploads succeed and
		// searches really hit PgVector, without needing a real Gemini API key
		@Bean
		@Primary
		EmbeddingModel fakeEmbeddingModel() {
			return new EmbeddingModel() {

				@Override
				public EmbeddingResponse call(EmbeddingRequest request) {
					List<Embedding> embeddings = new ArrayList<>();
					List<String> texts = request.getInstructions();
					for (int i = 0; i < texts.size(); i++) {
						embeddings.add(new Embedding(deterministicVector(texts.get(i)), i));
					}
					return new EmbeddingResponse(embeddings);
				}

				@Override
				public float[] embed(Document document) {
					return deterministicVector(getEmbeddingContent(document));
				}

				private float[] deterministicVector(String text) {
					String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
					Random random = new Random(normalized.hashCode());
					float[] vector = new float[768];
					for (int i = 0; i < vector.length; i++) {
						vector[i] = random.nextFloat();
					}
					return vector;
				}
			};
		}

	}

}
