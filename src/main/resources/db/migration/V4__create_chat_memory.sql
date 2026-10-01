-- Matches Spring AI's own schema-postgresql.sql, except conversation_id is widened:
-- our conversationId format is "tenantId:userId:sessionId" (~110 chars), not a single UUID.
CREATE TABLE spring_ai_chat_memory (
    conversation_id VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL,
    sequence_id BIGINT NOT NULL
);

CREATE INDEX idx_spring_ai_chat_memory_conversation_id_timestamp
ON spring_ai_chat_memory (conversation_id, "timestamp");

CREATE INDEX idx_spring_ai_chat_memory_conversation_id_sequence_id
ON spring_ai_chat_memory (conversation_id, sequence_id);
