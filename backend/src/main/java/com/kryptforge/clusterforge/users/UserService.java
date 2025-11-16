package com.kryptforge.clusterforge.users;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class UserService {

	private final UserRepository repository;
	private final PasswordEncoder passwordEncoder;
	private static final Logger log = LoggerFactory.getLogger(UserService.class);

	public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
	}

	public User create(String username, String password, Role role) {
		if (!StringUtils.hasText(username)) {
			throw new IllegalArgumentException("username vazio");
		}
		if (!StringUtils.hasText(password)) {
			throw new IllegalArgumentException("password vazio");
		}
		if (role == null) {
			throw new IllegalArgumentException("role vazio");
		}
		if (repository.existsByUsername(username)) {
			throw new IllegalArgumentException("username já utilizado");
		}

		User user = new User();
		user.setUsername(username);
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(role);
		
		User saved = repository.save(user);
		log.info("Usuário criado: {} com role: {}", username, role);
		return saved;
	}

	@Transactional(readOnly = true)
	public Optional<User> findByUsername(String username) {
		return repository.findByUsername(username);
	}

	@Transactional(readOnly = true)
	public Optional<User> findById(UUID id) {
		return repository.findById(id);
	}

	public boolean authenticate(String username, String password) {
		Optional<User> userOpt = repository.findByUsername(username);
		if (userOpt.isEmpty()) {
			return false;
		}
		User user = userOpt.get();
		return passwordEncoder.matches(password, user.getPassword());
	}
}

