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

import com.quidoitquoi.api.exceptions.GroupNotFoundException;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.Settlement;
import com.quidoitquoi.api.services.GroupService;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Group>> getGroupsByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(groupService.getGroupsByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Group> getGroupById(@PathVariable UUID id) {
        Group group = groupService.getGroupById(id)
                .orElseThrow(() -> new GroupNotFoundException(id));
        return ResponseEntity.ok(group);
    }

    @GetMapping("/{id}/settlements")
    public ResponseEntity<List<Settlement>> getSettlements(@PathVariable UUID id) {
        return ResponseEntity.ok(groupService.calculateSettlements(id));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<Group> createGroup(@PathVariable UUID userId, @RequestBody Group group) {
        Group createdGroup = groupService.createGroup(userId, group);
        return ResponseEntity.created(URI.create("/api/groups/" + createdGroup.getId()))
                .body(createdGroup);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Group> updateGroup(@PathVariable UUID id, @RequestBody Group group) {
        Group updatedGroup = groupService.updateGroup(id, group)
                .orElseThrow(() -> new GroupNotFoundException(id));
        return ResponseEntity.ok(updatedGroup);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        if (!groupService.deleteGroup(id)) {
            throw new GroupNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}