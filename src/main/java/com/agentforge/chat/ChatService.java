package com.agentforge.chat;

import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import com.agentforge.rag.RagPipeline;
import com.agentforge.rag.TenantAwareRetriever;
import com.agentforge.tenant.Tenant;
import com.agentforge.tenant.TenantRepository;

@Service
public class ChatService {

	private final ChatClient chatClient;
	private final MessageChatMemoryAdvisor chatMemoryAdvisor;
	private final ChatSessionService chatSessionService;
	private final TenantRepository tenantRepository;
	private final Map<String, RagPipeline> ragPipelines;
	private final String ragMode;
	private final Resource systemPromptResource;

	public ChatService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
			ChatSessionService chatSessionService, TenantRepository tenantRepository,
			Map<String, RagPipeline> ragPipelines, @Value("${agentforge.rag.mode}") String ragMode,
			@Value("classpath:/prompts/system.st") Resource systemPromptResource) {
		if (!ragPipelines.containsKey(ragMode)) {
			throw new IllegalStateException("agentforge.rag.mode='" + ragMode
					+ "' does not match any RagPipeline bean; valid values: " + ragPipelines.keySet());
		}
		this.chatClient = chatClientBuilder.build();
		this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
		this.chatSessionService = chatSessionService;
		this.tenantRepository = tenantRepository;
		this.ragPipelines = ragPipelines;
		this.ragMode = ragMode;
		this.systemPromptResource = systemPromptResource;
	}

	public String sendMessage(UUID sessionId, String userMessage) {
		ChatContext ctx = prepare(sessionId);
		String response = buildRequest(ctx, userMessage).call().content();
		chatSessionService.touch(ctx.session());
		return response;
	}

	// Ownership check and TenantContext access both happen in prepare(), synchronously, before
	// this method returns - nothing below this line, including doOnComplete, touches either one
	public Flux<String> streamMessage(UUID sessionId, String userMessage) {
		ChatContext ctx = prepare(sessionId);
		return buildRequest(ctx, userMessage).stream().content().doOnComplete(() -> chatSessionService.touch(ctx.session()));
	}

	// Ownership check (tenant + user) happens here, on the calling thread, before any
	// streaming chain exists - this is what makes another user's session 404 immediately,
	// not after the stream has already started
	private ChatContext prepare(UUID sessionId) {
		ChatSession session = chatSessionService.findOwnedSession(sessionId);
		UUID tenantId = session.getTenantId();
		Tenant tenant = tenantRepository.findById(tenantId)
			.orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));
		return new ChatContext(session, tenantId, ChatSessionService.conversationId(session),
				renderSystemPrompt(tenant));
	}

	// NoOpTemplateRenderer: systemText is already rendered, and userMessage is raw user
	// input - neither should be re-parsed as a template (would break on literal { } )
	private ChatClient.ChatClientRequestSpec buildRequest(ChatContext ctx, String userMessage) {
		return chatClient.prompt()
			.system(ctx.systemText())
			.user(userMessage)
			.templateRenderer(new NoOpTemplateRenderer())
			.advisors(a -> a.param(TenantAwareRetriever.TENANT_ID_CONTEXT_KEY, ctx.tenantId())
				.param(ChatMemory.CONVERSATION_ID, ctx.conversationId()))
			.advisors(chatMemoryAdvisor, ragPipelines.get(ragMode).advisor());
	}

	private String renderSystemPrompt(Tenant tenant) {
		PromptTemplate promptTemplate = new PromptTemplate(systemPromptResource);
		return promptTemplate.render(Map.of("tenantName", tenant.getName(), "systemPrompt",
				tenant.getSystemPrompt() == null ? "" : tenant.getSystemPrompt(), "tone",
				tenant.getTone() == null ? "" : tenant.getTone()));
	}

	private record ChatContext(ChatSession session, UUID tenantId, String conversationId, String systemText) {
	}

}
