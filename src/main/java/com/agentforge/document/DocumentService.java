package com.agentforge.document;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.agentforge.auth.TenantContext;
import com.agentforge.common.UnsupportedFileTypeException;

@Service
public class DocumentService {

	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");

	private final DocumentRepository documentRepository;
	private final VectorStore vectorStore;
	private final TenantContext tenantContext;
	private final int chunkSize;

	public DocumentService(DocumentRepository documentRepository, VectorStore vectorStore,
			TenantContext tenantContext, @Value("${agentforge.rag.chunk-size}") int chunkSize) {
		this.documentRepository = documentRepository;
		this.vectorStore = vectorStore;
		this.tenantContext = tenantContext;
		this.chunkSize = chunkSize;
	}

	public DocumentResponse uploadDocument(MultipartFile file) {
		return uploadDocument(file.getOriginalFilename(), file.getResource());
	}

	// Core logic, independent of MultipartFile, so non-HTTP callers (the demo seeder) go through
	// the exact same validation and ETL as a real upload, not a parallel bypass path
	public DocumentResponse uploadDocument(String filename, Resource resource) {
		validateFileType(filename);

		UUID tenantId = tenantContext.tenantId();
		Document document = new Document(tenantId, filename);
		documentRepository.save(document);

		document.markProcessing();
		documentRepository.save(document);

		try {
			List<org.springframework.ai.document.Document> chunks = readAndSplit(resource);

			if (chunks.isEmpty()) {
				document.markFailed("No extractable text found.");
			}
			else {
				for (org.springframework.ai.document.Document chunk : chunks) {
					chunk.getMetadata().put("tenantId", tenantId.toString());
					chunk.getMetadata().put("documentId", document.getId().toString());
					chunk.getMetadata().put("filename", filename);
				}
				vectorStore.add(chunks);
				document.markReady(chunks.size());
			}
		}
		catch (Exception ex) {
			log.error("Failed to process document {} ({})", document.getId(), filename, ex);
			deletePartialChunks(document.getId(), tenantId);
			document.markFailed("Failed to process document");
		}

		documentRepository.save(document);
		return DocumentResponse.from(document);
	}

	public List<DocumentResponse> listDocuments() {
		return documentRepository.findByTenantIdOrderByUploadedAtDesc(tenantContext.tenantId())
			.stream()
			.map(DocumentResponse::from)
			.toList();
	}

	private void validateFileType(String filename) {
		String extension = extractExtension(filename);
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new UnsupportedFileTypeException("Unsupported file type: " + extension);
		}
	}

	private String extractExtension(String filename) {
		if (filename == null || !filename.contains(".")) {
			return "";
		}
		return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
	}

	private List<org.springframework.ai.document.Document> readAndSplit(Resource resource) {
		TikaDocumentReader reader = new TikaDocumentReader(resource);
		List<org.springframework.ai.document.Document> pages = reader.get();
		TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(chunkSize).build();
		return splitter.split(pages);
	}

	// Failure here must not stop markFailed from being recorded - log and move on
	private void deletePartialChunks(UUID documentId, UUID tenantId) {
		try {
			FilterExpressionBuilder builder = new FilterExpressionBuilder();
			Filter.Expression filter = builder
				.and(builder.eq("documentId", documentId.toString()), builder.eq("tenantId", tenantId.toString()))
				.build();
			vectorStore.delete(filter);
		}
		catch (Exception cleanupEx) {
			log.error("Failed to clean up partial chunks for document {}", documentId, cleanupEx);
		}
	}

}
