package com.quidoitquoi.api.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.quidoitquoi.api.exceptions.GroupNotFoundException;
import com.quidoitquoi.api.dto.GroupLogResponse;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.Settlement;
import com.quidoitquoi.api.services.GroupService;

@RestController
@Validated
@RequestMapping("/api/groups")
@Tag(name = "Groups", description = "Shared expense group management and settlement calculations")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List the authenticated user's groups", description = "Returns groups in which the authenticated user is an admin or member.")
    @ApiResponse(responseCode = "200", description = "Groups returned")
    public ResponseEntity<List<Group>> getGroupsByUserId(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(groupService.getGroupsByUserId(userId, authenticatedUserId(jwt)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a group", description = "Returns one group by UUID to an authenticated group member.")
    @ApiResponse(responseCode = "200", description = "Group returned")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<Group> getGroupById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        Group group = groupService.getGroupById(id, authenticatedUserId(jwt))
                .orElseThrow(() -> new GroupNotFoundException(id));
        return ResponseEntity.ok(group);
    }

    @GetMapping("/{id}/logs")
    @Operation(
            summary = "List a group's activity logs",
            description = "Returns a page of group activity logs to a group member, with a maximum of 10 entries per page.")
    @ApiResponse(responseCode = "200", description = "Group logs returned")
    @ApiResponse(responseCode = "400", description = "Page must be zero or greater and size must be positive")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<Page<GroupLogResponse>> getGroupLogs(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(groupService.getGroupLogs(id, page, size, authenticatedUserId(jwt)));
    }

    @GetMapping("/{id}/settlements")
    @Operation(summary = "Calculate settlements", description = "Calculates transfers for an authenticated group member.")
    @ApiResponse(responseCode = "200", description = "Settlement transfers returned")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<List<Settlement>> getSettlements(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(groupService.calculateSettlements(id, authenticatedUserId(jwt)));
    }

    @PostMapping("/{userId}")
    @Operation(summary = "Create a group", description = "Creates a group owned by the authenticated user.")
    @ApiResponse(responseCode = "201", description = "Group created")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Group> createGroup(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody Group group) {
        Group createdGroup = groupService.createGroup(userId, group, authenticatedUserId(jwt));
        return ResponseEntity.created(URI.create("/api/groups/" + createdGroup.getId()))
                .body(createdGroup);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a group", description = "Updates an existing group by UUID. Only its admin may update it.")
    @ApiResponse(responseCode = "200", description = "Group updated")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<Group> updateGroup(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody Group group) {
        UUID madeByUserId = authenticatedUserId(jwt);
        Group updatedGroup = groupService.updateGroup(id, group, madeByUserId)
                .orElseThrow(() -> new GroupNotFoundException(id));
        return ResponseEntity.ok(updatedGroup);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a group", description = "Deletes a group by UUID. Only its admin may delete it.")
    @ApiResponse(responseCode = "204", description = "Group deleted")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        if (!groupService.deleteGroup(id, authenticatedUserId(jwt))) {
            throw new GroupNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}