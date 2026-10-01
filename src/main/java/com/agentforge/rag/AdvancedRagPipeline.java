package com.agentforge.rag;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Bean name "advanced" matches agentforge.rag.mode's value, for lookup by ChatService
@Component("advanced")
public class AdvancedRagPipeline implements RagPipeline {

	private final Advisor advisor;

	public AdvancedRagPipeline(TenantAwareRetriever tenantAwareRetriever, ChatClient.Builder chatClientBuilder,
			@Value("${agentforge.rag.multi-query-count}") int multiQueryCount) {
		RewriteQueryTransformer rewriteQueryTransformer = RewriteQueryTransformer.builder()
			.chatClientBuilder(chatClientBuilder)
			.build();

		MultiQueryExpander multiQueryExpander = MultiQueryExpander.builder()
			.chatClientBuilder(chatClientBuilder)
			.numberOfQueries(multiQueryCount)
			.build();

		// Same TenantAwareRetriever as SimpleRagPipeline - every expanded/rewritten query still
		// carries tenantId through Query.context(), so retrieval stays tenant-filtered either way
		this.advisor = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(tenantAwareRetriever)
			.queryTransformers(List.of(rewriteQueryTransformer))
			.queryExpander(multiQueryExpander)
			.build();
	}

	@Override
	public Advisor advisor() {
		return advisor;
	}

}
