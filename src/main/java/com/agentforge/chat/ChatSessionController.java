package com.agentforge.chat;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/sessions")
public class ChatSessionController {

	private final ChatSessionService chatSessionService;
	private final ChatService chatService;

	public ChatSessionController(ChatSessionService chatSessionService, ChatService chatService) {
		this.chatSessionService = chatSessionService;
		this.chatService = chatService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ChatSessionResponse create(@Valid @RequestBody CreateChatSessionRequest request) {
		return chatSessionService.createSession(request);
	}

	@GetMapping
	public List<ChatSessionResponse> list() {
		return chatSessionService.listSessions();
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		chatSessionService.deleteSession(id);
	}

	// Permanent synchronous endpoint; separate from the SSE route below
	@PostMapping("/{id}/messages")
	public SendMessageResponse sendMessage(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
		return new SendMessageResponse(chatService.sendMessage(id, request.message()));
	}

	@PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> streamMessage(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
		return chatService.streamMessage(id, request.message());
	}

}
 