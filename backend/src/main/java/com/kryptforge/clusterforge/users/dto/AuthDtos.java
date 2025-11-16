package com.kryptforge.clusterforge.users.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

	public record LoginRequest(
		@NotBlank(message = "username é obrigatório")
		String username,
		
		@NotBlank(message = "password é obrigatório")
		String password
	) {}

	public record LoginResponse(
		String token,
		String username,
		String role,
		String userId
	) {}

	public record RegisterRequest(
		@NotBlank(message = "username é obrigatório")
		String username,
		
		@NotBlank(message = "password é obrigatório")
		String password
	) {}

	public record UserResponse(
		String id,
		String username,
		String role
	) {}
}

