package com.kryptforge.clusterforge.users;

import java.util.UUID;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

	public Optional<User> getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof User) {
			return Optional.of((User) authentication.getPrincipal());
		}
		return Optional.empty();
	}

	public Optional<UUID> getCurrentUserId() {
		return getCurrentUser().map(User::getId);
	}

	public boolean isAdmin() {
		return getCurrentUser()
			.map(user -> user.getRole() == Role.ADMIN)
			.orElse(false);
	}

	public boolean isUser() {
		return getCurrentUser()
			.map(user -> user.getRole() == Role.USER)
			.orElse(false);
	}
}

