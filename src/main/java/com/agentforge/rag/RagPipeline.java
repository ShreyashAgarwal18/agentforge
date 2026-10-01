package com.agentforge.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;

public interface RagPipeline {

	Advisor advisor();

}
