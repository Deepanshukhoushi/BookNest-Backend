package com.booknest.authservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.booknest.authservice.entity.User;

/**
 * Repository interface for performing database operations on User entities.
 */
public interface UserRepository extends JpaRepository<User, Long>{
	
	// Retrieves a user profile based on their email address
	Optional<User> findByEmail(String email);
	// Retrieves a user profile based on their internal ID
	Optional<User> findByUserId(Long userId);
	// Verifies if an email address is already registered in the system
	boolean existsByEmail(String email);
	// Returns a list of all users who share a specific security role
	List<User> findAllByRole(String role);
	// Permanently deletes a user from the system archives
	void deleteByUserId(Long userId);
}
