package com.quidoitquoi.api.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.quidoitquoi.api.exceptions.ForbiddenOperationException;
import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.services.UserService;
import com.quidoitquoi.api.services.GroupImageStorageService;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User account management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
	private final UserService userService;
	private final GroupImageStorageService imageStorageService;

	public UserController(UserService userService, GroupImageStorageService imageStorageService) {
		this.userService = userService;
		this.imageStorageService = imageStorageService;
	}

	@GetMapping
	@Operation(summary = "List users", description = "Listing all user profiles is not allowed.")
	@ApiResponse(responseCode = "403", description = "Listing all user profiles is forbidden")
	public ResponseEntity<List<User>> getAllUsers() {
		throw new ForbiddenOperationException();
	}

	@GetMapping("/me")
	@Operation(summary = "Get the current user", description = "Returns the authenticated user.")
	@ApiResponse(responseCode = "200", description = "Current user returned")
	public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
		UUID id = UUID.fromString(jwt.getSubject());
		User user = userService.getUserById(id)
				.orElseThrow(() -> new UserNotFoundException(id));
		return ResponseEntity.ok(user);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a user", description = "Returns one user by UUID.")
	@ApiResponse(responseCode = "200", description = "User returned")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<User> getUserById(
			@PathVariable UUID id,
			@AuthenticationPrincipal Jwt jwt) {
		requireCurrentUser(id, jwt);
		User user = userService.getUserById(id)
				.orElseThrow(() -> new UserNotFoundException(id));
		return ResponseEntity.ok(user);
	}

	@GetMapping("/{id}/image/{imageId}")
	@Operation(summary = "Get a profile image", description = "Returns an uploaded image belonging to the authenticated user.")
	@ApiResponse(responseCode = "200", description = "Profile image returned")
	@ApiResponse(responseCode = "404", description = "Image not found")
	public ResponseEntity<byte[]> getUserImage(
			@PathVariable UUID id,
			@PathVariable UUID imageId,
			@AuthenticationPrincipal Jwt jwt) {
		requireCurrentUser(id, jwt);
		return imageStorageService.loadUserImage(id, imageId)
				.map(image -> ResponseEntity.ok()
						.contentType(image.mediaType())
						.cacheControl(CacheControl.noStore())
						.body(image.content()))
				.orElseGet(() -> ResponseEntity.notFound().build());
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
	public ResponseEntity<User> updateUser(
			@PathVariable UUID id,
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody User user) {
		requireCurrentUser(id, jwt);
		User updatedUser = userService.updateUser(id, user)
				.orElseThrow(() -> new UserNotFoundException(id));
		return ResponseEntity.ok(updatedUser);
	}

	@PutMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Upload a profile image", description = "Uploads a PNG or JPEG profile image for the authenticated user.")
	@ApiResponse(responseCode = "200", description = "Profile image uploaded")
	@ApiResponse(responseCode = "400", description = "Invalid image")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<User> updateUserImage(
			@PathVariable UUID id,
			@RequestPart("image") MultipartFile image,
			@AuthenticationPrincipal Jwt jwt) {
		requireCurrentUser(id, jwt);
		User updatedUser = userService.updateUserImage(id, image)
				.orElseThrow(() -> new UserNotFoundException(id));
		return ResponseEntity.ok(updatedUser);
	}

	@DeleteMapping("/{id}/image")
	@Operation(summary = "Delete a profile image", description = "Removes the authenticated user's profile image.")
	@ApiResponse(responseCode = "200", description = "Profile image removed")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<User> deleteUserImage(
			@PathVariable UUID id,
			@AuthenticationPrincipal Jwt jwt) {
		requireCurrentUser(id, jwt);
		User updatedUser = userService.deleteUserImage(id)
				.orElseThrow(() -> new UserNotFoundException(id));
		return ResponseEntity.ok(updatedUser);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a user", description = "Deletes a user by UUID.")
	@ApiResponse(responseCode = "204", description = "User deleted")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<Void> deleteUser(
			@PathVariable UUID id,
			@AuthenticationPrincipal Jwt jwt) {
		requireCurrentUser(id, jwt);
		if (!userService.deleteUser(id)) {
			throw new UserNotFoundException(id);
		}
		return ResponseEntity.noContent().build();
	}

	private void requireCurrentUser(UUID requestedUserId, Jwt jwt) {
		if (!requestedUserId.equals(UUID.fromString(jwt.getSubject()))) {
			throw new ForbiddenOperationException();
		}
	}
}
