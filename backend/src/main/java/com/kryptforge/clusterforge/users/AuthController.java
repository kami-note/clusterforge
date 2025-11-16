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
import com.kryptforge.clusterforge.users.dto.AuthDtos.RegisterRequest;

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

	@PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
		// Verifica se o username já existe
		if (userService.findByUsername(request.username()).isPresent()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username já utilizado");
		}

		// Cria o usuário (primeiro usuário será ADMIN, demais serão USER)
		User user = userService.register(request.username(), request.password());

		// Gera token JWT automaticamente após registro
		String token = jwtService.generateToken(user);

		return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(
			token,
			user.getUsername(),
			user.getRole().name(),
			user.getId().toString()
		));
	}
}

