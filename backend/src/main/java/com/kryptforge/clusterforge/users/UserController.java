package com.kryptforge.clusterforge.users;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(path = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

	private final UserService userService;
	private final CurrentUser currentUser;

	public UserController(UserService userService, CurrentUser currentUser) {
		this.userService = userService;
		this.currentUser = currentUser;
	}

	@GetMapping
	public List<UserSummary> listUsers() {
		User requester = currentUser.getCurrentUser()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "não autenticado"));

		if (requester.getRole() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "apenas administradores podem listar usuários");
		}

		return userService.listAll().stream()
			.map(user -> new UserSummary(
				user.getId().toString(),
				user.getUsername(),
				user.getRole().name()
			))
			.toList();
	}

	public record UserSummary(
		String id,
		String username,
		String role
	) {}
}

