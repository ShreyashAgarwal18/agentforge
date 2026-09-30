package com.agentforge.document;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	private final DocumentService documentService;

	public DocumentController(DocumentService documentService) {
		this.documentService = documentService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentResponse upload(@RequestParam("file") MultipartFile file) {
		return documentService.uploadDocument(file);
	}

	@GetMapping
	public List<DocumentResponse> list() {
		return documentService.listDocuments();
	}

}
