package com.agentforge.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	List<User> findByTenantId(UUID tenantId);

	Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByRole(Role role);

}
