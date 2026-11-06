package com.vyapaaros.user.repository;

import com.vyapaaros.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Load a user with their roles eagerly for JWT revalidation.
     * Used by JwtAuthenticationFilter to verify current active state and roles.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(UUID id);
}
