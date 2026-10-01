package com.agentforge.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Component;

// Bean name "simple" matches agentforge.rag.mode's value, for lookup by ChatService
@Component("simple")
public class SimpleRagPipeline implements RagPipeline {

	private final TenantAwareRetriever tenantAwareRetriever;

	public SimpleRagPipeline(TenantAwareRetriever tenantAwareRetriever) {
		this.tenantAwareRetriever = tenantAwareRetriever;
	}

	@Override
	public Advisor advisor() {
		return RetrievalAugmentationAdvisor.builder().documentRetriever(tenantAwareRetriever).build();
	}

}
