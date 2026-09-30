package com.agentforge.rag;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agentforge.common.TenantAccessDeniedException;

@Component
public class TenantAwareRetriever implements DocumentRetriever {

	public static final String TENANT_ID_CONTEXT_KEY = "tenantId";

	private final VectorStore vectorStore;
	private final int topK;
	private final double similarityThreshold;

	public TenantAwareRetriever(VectorStore vectorStore, @Value("${agentforge.rag.top-k}") int topK,
			@Value("${agentforge.rag.similarity-threshold}") double similarityThreshold) {
		this.vectorStore = vectorStore;
		this.topK = topK;
		this.similarityThreshold = similarityThreshold;
	}

	// Pipeline entry point: tenantId must already be in the context by the time this runs
	@Override
	public List<Document> retrieve(Query query) {
		Object tenantId = query.context().get(TENANT_ID_CONTEXT_KEY);
		if (!(tenantId instanceof UUID tenantUuid)) {
			throw new TenantAccessDeniedException("Query has no tenantId in its context");
		}
		return search(query.text(), tenantUuid);
	}

	// Direct entry point for callers outside the RAG pipeline machinery
	public List<Document> retrieve(String queryText, UUID tenantId) {
		Objects.requireNonNull(tenantId, "tenantId is required");
		return search(queryText, tenantId);
	}

	private List<Document> search(String queryText, UUID tenantId) {
		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		Filter.Expression filter = builder.eq("tenantId", tenantId.toString()).build();

		SearchRequest request = SearchRequest.builder()
			.query(queryText)
			.topK(topK)
			.similarityThreshold(similarityThreshold)
			.filterExpression(filter)
			.build();

		return vectorStore.similaritySearch(request);
	}

}
