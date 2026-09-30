package com.agentforge.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	List<Document> findByTenantIdOrderByUploadedAtDesc(UUID tenantId);

	Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

}
