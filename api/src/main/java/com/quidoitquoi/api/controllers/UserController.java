package com.quidoitquoi.api.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.services.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public ResponseEntity<List<User>> getAllUsers() {
		return ResponseEntity.ok(userService.getAllUsers());
	}

	@GetMapping("/{id}")
	public ResponseEntity<User> getUserById(@PathVariable UUID id) {
		User user = userService.getUserById(id)
				.orElseThrow(() -> new UserNotFoundException(id));
		return ResponseEntity.ok(user);
	}

	@PostMapping
	public ResponseEntity<User> createUser(@RequestBody User user) {
		User createdUser = userService.createUser(user);
		return ResponseEntity.created(URI.create("/api/users/" + createdUser.getId()))
				.body(createdUser);
	}

	@PutMapping("/{id}")
	public ResponseEntity<User> updateUser(@PathVariable UUID id, @RequestBody User user) {
		User updatedUser = userService.updateUser(id, user)
				.orElseThrow(() -> new UserNotFoundException(id));
		return ResponseEntity.ok(updatedUser);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
		if (!userService.deleteUser(id)) {
			throw new UserNotFoundException(id);
		}
		return ResponseEntity.noContent().build();
	}
}
