package com.agentforge.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private String filename;

	@Column(name = "content_hash")
	private String contentHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DocumentStatus status;

	@Column(name = "chunk_count", nullable = false)
	private int chunkCount;

	private String error;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	protected Document() {
	}

	public Document(UUID tenantId, String filename) {
		this.tenantId = tenantId;
		this.filename = filename;
		this.status = DocumentStatus.PENDING;
		this.chunkCount = 0;
		this.uploadedAt = Instant.now();
	}

	public void markProcessing() {
		this.status = DocumentStatus.PROCESSING;
	}

	public void markReady(int chunkCount) {
		this.status = DocumentStatus.READY;
		this.chunkCount = chunkCount;
	}

	public void markFailed(String error) {
		this.status = DocumentStatus.FAILED;
		this.error = error;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getFilename() {
		return filename;
	}

	public String getContentHash() {
		return contentHash;
	}

	public DocumentStatus getStatus() {
		return status;
	}

	public int getChunkCount() {
		return chunkCount;
	}

	public String getError() {
		return error;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

}
