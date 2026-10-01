package com.agentforge.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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
import com.agentforge.tenant.CreateTenantRequest;
import com.agentforge.tenant.TenantResponse;
import com.agentforge.user.CreateUserRequest;
import com.agentforge.user.UserResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Real end-to-end test, not opt-in - uses Testcontainers and a fake ChatModel, not real Gemini
@Testcontainers
@AutoConfigureTestRestTemplate
@Import(ChatServiceTest.FakeChatModelConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "GEMINI_API_KEY=dummy-test-key",
				"AGENTFORGE_JWT_SECRET=integration-test-jwt-secret-value-1234567890",
				"AGENTFORGE_ADMIN_PASSWORD=test-admin-password" })
class ChatServiceTest {

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

	@org.springframework.boot.test.web.server.LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ChatService chatService;

	@Autowired
	private FakeChatModel fakeChatModel;

	@Test
	void messageAndTenantPromptContainingBracesDoNotBreakRendering() {
		String platformAdminToken = login("admin@agentforge.local", "test-admin-password");

		// Tenant system prompt itself contains braces
		CreateTenantRequest createTenant = new CreateTenantRequest("Brace Shop", "brace-shop",
				"Always answer in JSON like {\"answer\": \"...\"}.", null, "admin@brace.test",
				"tenant-admin-password");
		TenantResponse tenant = createTenant(platformAdminToken, createTenant);

		String tenantAdminToken = login("admin@brace.test", "tenant-admin-password");
		UUID userId = createEndUser(tenantAdminToken, "user@brace.test", "end-user-password");
		UUID sessionId = createSession(login("user@brace.test", "end-user-password"));

		runAs(userId, tenant.id(), () -> {
			// User message itself contains braces (e.g. a pasted JSON snippet)
			String response = chatService.sendMessage(sessionId, "Here is my data: {\"a\": 1, \"b\": 2}");
			assertThat(response).isNotNull();
		});
	}

	@Test
	void secondMessageIncludesFirstExchangeFromMemory() {
		String platformAdminToken = login("admin@agentforge.local", "test-admin-password");
		TenantResponse tenant = createTenant(platformAdminToken,
				new CreateTenantRequest("Memory Cafe", "memory-cafe", null, null, "admin@memorycafe.test",
						"tenant-admin-password"));
		String tenantAdminToken = login("admin@memorycafe.test", "tenant-admin-password");
		UUID userId = createEndUser(tenantAdminToken, "user@memorycafe.test", "end-user-password");
		UUID sessionId = createSession(login("user@memorycafe.test", "end-user-password"));

		runAs(userId, tenant.id(), () -> {
			chatService.sendMessage(sessionId, "My name is Alice.");
			chatService.sendMessage(sessionId, "What is my name?");
		});

		List<Prompt> prompts = fakeChatModel.getCapturedPrompts();
		assertThat(prompts).hasSize(2);

		List<Message> secondPromptMessages = prompts.get(1).getInstructions();
		boolean firstExchangePresent = secondPromptMessages.stream()
			.anyMatch(message -> message.getText() != null && message.getText().contains("My name is Alice."));
		assertThat(firstExchangePresent).isTrue();
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

	private TenantResponse createTenant(String platformAdminToken, CreateTenantRequest request) {
		ResponseEntity<TenantResponse> response = restTemplate.exchange(url("/api/platform/tenants"),
				HttpMethod.POST, authorized(request, platformAdminToken), TenantResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private UUID createEndUser(String tenantAdminToken, String email, String password) {
		ResponseEntity<UserResponse> response = restTemplate.exchange(url("/api/users"), HttpMethod.POST,
				authorized(new CreateUserRequest(email, password), tenantAdminToken), UserResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody().id();
	}

	private UUID createSession(String userToken) {
		ResponseEntity<ChatSessionResponse> response = restTemplate.exchange(url("/api/sessions"), HttpMethod.POST,
				authorized(new CreateChatSessionRequest(null), userToken), ChatSessionResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody().id();
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

	static class FakeChatModel implements ChatModel {

		private final List<Prompt> capturedPrompts = new ArrayList<>();

		@Override
		public ChatResponse call(Prompt prompt) {
			capturedPrompts.add(prompt);
			return new ChatResponse(List.of(new Generation(new AssistantMessage("Fake response " + capturedPrompts.size()))));
		}

		List<Prompt> getCapturedPrompts() {
			return capturedPrompts;
		}

	}

	@TestConfiguration
	static class FakeChatModelConfig {

		@Bean
		@Primary
		FakeChatModel fakeChatModel() {
			return new FakeChatModel();
		}

		// The RAG pipeline always calls TenantAwareRetriever, which needs an EmbeddingModel
		// to embed the search query - fake it too so this test never touches real Gemini
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
