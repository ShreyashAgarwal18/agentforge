package com.agentforge;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// Opt-in only: class name ends in "IT" so `mvn test` skips it; run with -Dtest=GeminiPgVectorSmokeIT
// Docker Compose is skipped in tests by default (Boot 4.1.1) - re-enabled here for the real Postgres connection
@SpringBootTest(properties = "spring.docker.compose.skip.in-tests=false")
class GeminiPgVectorSmokeIT {

	@Autowired
	private ChatModel chatModel;

	@Autowired
	private EmbeddingModel embeddingModel;

	@Autowired
	private VectorStore vectorStore;

	@Value("${agentforge.embedding.dimensions}")
	private int expectedDimensions;

	private String storedDocumentId;

	@AfterEach
	void cleanUp() {
		if (storedDocumentId != null) {
			vectorStore.delete(List.of(storedDocumentId));
		}
	}

	@Test
	void chatCallReturnsNonEmptyReply() {
		String reply = chatModel.call("Reply with the single word: pong");
		assertThat(reply).isNotBlank();
	}

	@Test
	void embeddingCallReturnsVectorWithConfiguredDimension() {
		float[] vector = embeddingModel.embed("AgentForge smoke test");
		assertThat(vector).hasSize(expectedDimensions);
	}

	// Calls VectorStore directly - TenantAwareRetriever doesn't exist yet (Milestone 1 task 4)
	@Test
	void vectorStoreStoresAndRetrievesDocumentBySimilarity() {
		Document document = Document.builder()
			.text("AgentForge smoke test marker: pumpkins are orange root vegetables")
			.build();
		storedDocumentId = document.getId();
		vectorStore.add(List.of(document));

		List<Document> results = vectorStore
			.similaritySearch(SearchRequest.builder().query("orange root vegetable").topK(1).build());

		assertThat(results).extracting(Document::getId).contains(storedDocumentId);
	}

}
