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

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.services.UserService;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User account management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	@Operation(summary = "List users", description = "Returns all users.")
	@ApiResponse(responseCode = "200", description = "Users returned")
	public ResponseEntity<List<User>> getAllUsers() {
		return ResponseEntity.ok(userService.getAllUsers());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a user", description = "Returns one user by UUID.")
	@ApiResponse(responseCode = "200", description = "User returned")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<User> getUserById(@PathVariable UUID id) {
		User user = userService.getUserById(id)
				.orElseThrow(() -> new UserNotFoundException(id));
		return ResponseEntity.ok(user);
	}

	@PostMapping
	@Operation(summary = "Create a user", description = "Creates a user from the validated request body.")
	@ApiResponse(responseCode = "201", description = "User created")
	@ApiResponse(responseCode = "400", description = "Request validation failed")
	public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
		User createdUser = userService.createUser(user);
		return ResponseEntity.created(URI.create("/api/users/" + createdUser.getId()))
				.body(createdUser);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a user", description = "Updates an existing user by UUID.")
	@ApiResponse(responseCode = "200", description = "User updated")
	@ApiResponse(responseCode = "400", description = "Request validation failed")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<User> updateUser(@PathVariable UUID id, @Valid @RequestBody User user) {
		User updatedUser = userService.updateUser(id, user)
				.orElseThrow(() -> new UserNotFoundException(id));
		return ResponseEntity.ok(updatedUser);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a user", description = "Deletes a user by UUID.")
	@ApiResponse(responseCode = "204", description = "User deleted")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
		if (!userService.deleteUser(id)) {
			throw new UserNotFoundException(id);
		}
		return ResponseEntity.noContent().build();
	}
}
