package com.kryptforge.clusterforge.users;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.kryptforge.clusterforge.users.dto.AuthDtos.LoginRequest;
import com.kryptforge.clusterforge.users.dto.AuthDtos.LoginResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

	private final UserService userService;
	private final JwtService jwtService;

	public AuthController(UserService userService, JwtService jwtService) {
		this.userService = userService;
		this.jwtService = jwtService;
	}

	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		User user = userService.findByUsername(request.username())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciais inválidas"));

		// Verifica credenciais
		if (!userService.authenticate(request.username(), request.password())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciais inválidas");
		}

		// Gera token JWT
		String token = jwtService.generateToken(user);

		return ResponseEntity.ok(new LoginResponse(
			token,
			user.getUsername(),
			user.getRole().name(),
			user.getId().toString()
		));
	}
}

