package com.agentforge.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
		UUID id,
		String filename,
		DocumentStatus status,
		int chunkCount,
		String error,
		Instant uploadedAt) {

	public static DocumentResponse from(Document document) {
		return new DocumentResponse(document.getId(), document.getFilename(), document.getStatus(),
				document.getChunkCount(), document.getError(), document.getUploadedAt());
	}

}
