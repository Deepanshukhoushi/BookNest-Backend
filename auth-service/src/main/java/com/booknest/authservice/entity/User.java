package com.booknest.authservice.entity;

import com.booknest.authservice.enums.AuthProvider;
import java.time.LocalDateTime;

import com.booknest.authservice.enums.Role;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "Customer", description = "Maps to Customer terminology in domain, representing the core registered user.")
@Table(name = "users", uniqueConstraints = {
		@UniqueConstraint(columnNames = "email")
})

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

/**
 * Entity class representing a registered user in the system.
 * This maps to the 'users' table and stores profile, role, and authentication data.
 */
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long userId;
	
	@NotBlank(message = "Full name is required")
	@Size(min = 3, max = 50)
	@Pattern(regexp = "^[A-Z][a-z]+(\\s[A-Z][a-z]+)*$", message = "Each word in name must start with a capital letter")
	private String fullName;
	
	@NotBlank(message = "Email is required")
	@Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "Invalid email format")
	@Column(nullable = false, unique = true)
	private String email;
	
	@Column(nullable = true)
	private String passwordHash;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;
	
	@Enumerated(EnumType.STRING)
	private AuthProvider provider;
	
	@Pattern(regexp = "^[6-9][0-9]{9}$", message = "Mobile number must be 10 digits and start with 6-9")
	private String mobile;

	@Lob
	@Column(columnDefinition = "LONGTEXT")
	private String profileImage;

	@Builder.Default
	@Column(nullable = false)
	private Boolean suspended = false;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
