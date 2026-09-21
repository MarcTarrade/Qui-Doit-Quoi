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

import com.quidoitquoi.api.exceptions.GroupNotFoundException;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.Settlement;
import com.quidoitquoi.api.services.GroupService;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "Groups", description = "Shared expense group management and settlement calculations")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List a user's groups", description = "Returns every group associated with the specified user UUID.")
    @ApiResponse(responseCode = "200", description = "Groups returned")
    public ResponseEntity<List<Group>> getGroupsByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(groupService.getGroupsByUserId(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a group", description = "Returns one group by UUID.")
    @ApiResponse(responseCode = "200", description = "Group returned")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<Group> getGroupById(@PathVariable UUID id) {
        Group group = groupService.getGroupById(id)
                .orElseThrow(() -> new GroupNotFoundException(id));
        return ResponseEntity.ok(group);
    }

    @GetMapping("/{id}/settlements")
    @Operation(summary = "Calculate settlements", description = "Calculates the transfers needed to settle the group's shared expenses.")
    @ApiResponse(responseCode = "200", description = "Settlement transfers returned")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<List<Settlement>> getSettlements(@PathVariable UUID id) {
        return ResponseEntity.ok(groupService.calculateSettlements(id));
    }

    @PostMapping("/{userId}")
    @Operation(summary = "Create a group", description = "Creates a group owned by the specified user UUID.")
    @ApiResponse(responseCode = "201", description = "Group created")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Group> createGroup(@PathVariable UUID userId, @Valid @RequestBody Group group) {
        Group createdGroup = groupService.createGroup(userId, group);
        return ResponseEntity.created(URI.create("/api/groups/" + createdGroup.getId()))
                .body(createdGroup);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a group", description = "Updates an existing group by UUID.")
    @ApiResponse(responseCode = "200", description = "Group updated")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<Group> updateGroup(@PathVariable UUID id, @Valid @RequestBody Group group) {
        Group updatedGroup = groupService.updateGroup(id, group)
                .orElseThrow(() -> new GroupNotFoundException(id));
        return ResponseEntity.ok(updatedGroup);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a group", description = "Deletes a group by UUID.")
    @ApiResponse(responseCode = "204", description = "Group deleted")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        if (!groupService.deleteGroup(id)) {
            throw new GroupNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}