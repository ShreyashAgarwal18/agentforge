package com.agentforge.rag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.agentforge.auth.LoginRequest;
import com.agentforge.auth.LoginResponse;
import com.agentforge.chat.ChatService;
import com.agentforge.chat.ChatSessionResponse;
import com.agentforge.chat.CreateChatSessionRequest;
import com.agentforge.tenant.CreateTenantRequest;
import com.agentforge.tenant.TenantResponse;
import com.agentforge.user.CreateUserRequest;
import com.agentforge.user.UserResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Real end-to-end test, not opt-in - uses Testcontainers and fakes, not real Gemini
@Testcontainers
@AutoConfigureTestRestTemplate
@Import(AdvancedRagPipelineTest.FakeModelConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "GEMINI_API_KEY=dummy-test-key",
				"AGENTFORGE_JWT_SECRET=integration-test-jwt-secret-value-1234567890",
				"AGENTFORGE_ADMIN_PASSWORD=test-admin-password", "agentforge.rag.mode=advanced" })
class AdvancedRagPipelineTest {

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
	private ChatService chatService;

	// Proof that tenantId survives RewriteQueryTransformer and MultiQueryExpander: if it were lost
	// anywhere along the chain, TenantAwareRetriever.retrieve(Query) would throw for every expanded
	// query, and this call would fail instead of completing.
	@Test
	void tenantIdSurvivesRewritingAndExpansion() {
		String platformAdminToken = login("admin@agentforge.local", "test-admin-password");

		ResponseEntity<TenantResponse> tenantResponse = restTemplate.exchange(url("/api/platform/tenants"),
				HttpMethod.POST,
				authorized(new CreateTenantRequest("Advanced Cafe", "advanced-cafe", null, null,
						"admin@advancedcafe.test", "tenant-admin-password"), platformAdminToken),
				TenantResponse.class);
		assertThat(tenantResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		UUID tenantId = tenantResponse.getBody().id();

		String tenantAdminToken = login("admin@advancedcafe.test", "tenant-admin-password");

		ResponseEntity<UserResponse> userResponse = restTemplate.exchange(url("/api/users"), HttpMethod.POST,
				authorized(new CreateUserRequest("user@advancedcafe.test", "end-user-password"), tenantAdminToken),
				UserResponse.class);
		assertThat(userResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		UUID userId = userResponse.getBody().id();

		String userToken = login("user@advancedcafe.test", "end-user-password");
		ResponseEntity<ChatSessionResponse> sessionResponse = restTemplate.exchange(url("/api/sessions"),
				HttpMethod.POST, authorized(new CreateChatSessionRequest(null), userToken),
				ChatSessionResponse.class);
		assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		UUID sessionId = sessionResponse.getBody().id();

		runAs(userId, tenantId, () -> {
			String response = chatService.sendMessage(sessionId, "What are your opening hours?");
			assertThat(response).isNotNull();
		});
	}

	private void runAs(UUID userId, UUID tenantId, Runnable action) {
		Instant now = Instant.now();
		Jwt jwt = Jwt.withTokenValue("test-token")
			.header("alg", "none")
			.issuedAt(now)
			.expiresAt(now.plusSeconds(3600))
			.subject(userId.toString())
			.claim("tenantId", tenantId.toString())
			.claim("role", "END_USER")
			.build();
		var authorities = List.of(new SimpleGrantedAuthority("ROLE_END_USER"));
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
		try {
			action.run();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
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

	@TestConfiguration
	static class FakeModelConfig {

		// Always 3 lines, matching agentforge.rag.multi-query-count=3, so MultiQueryExpander
		// actually builds 3 variants instead of falling back to the unchanged original query
		@Bean
		@Primary
		ChatModel fakeChatModel() {
			return new ChatModel() {

				@Override
				public ChatResponse call(Prompt prompt) {
					String fakeReply = "variant one\nvariant two\nvariant three";
					return new ChatResponse(List.of(new Generation(new AssistantMessage(fakeReply))));
				}
			};
		}

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
