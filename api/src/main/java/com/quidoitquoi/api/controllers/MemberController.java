package com.quidoitquoi.api.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.quidoitquoi.api.exceptions.MemberNotFoundException;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.services.MemberService;

@RestController
@RequestMapping("/api")
@Tag(name = "Members", description = "Group member management")
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/groups/{groupId}/members")
    @Operation(summary = "List group members", description = "Returns members to an authenticated group member.")
    @ApiResponse(responseCode = "200", description = "Members returned")
    public ResponseEntity<List<Member>> getMembersByGroupId(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(memberService.getMembersByGroupId(groupId, authenticatedUserId(jwt)));
    }

    @GetMapping("/members/{id}")
    @Operation(summary = "Get a member", description = "Returns one member by UUID.")
    @ApiResponse(responseCode = "200", description = "Member returned")
    @ApiResponse(responseCode = "404", description = "Member not found")
    public ResponseEntity<Member> getMemberById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        Member member = memberService.getMemberById(id, authenticatedUserId(jwt))
            .orElseThrow(() -> new MemberNotFoundException(id));
        return ResponseEntity.ok(member);
    }

    @PostMapping("/groups/{groupId}/members")
    @Operation(summary = "Add a member", description = "Adds a validated member to a group. Only its admin may add members.")
    @ApiResponse(responseCode = "201", description = "Member created")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<Member> addMember(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody Member member) {
        Member createdMember = memberService.addMember(groupId, member, authenticatedUserId(jwt));
        return ResponseEntity.created(URI.create("/api/members/" + createdMember.getId()))
                .body(createdMember);
    }

    @PutMapping("/members/{id}")
    @Operation(summary = "Update a member", description = "Updates a member. Only its group admin may update members.")
    @ApiResponse(responseCode = "200", description = "Member updated")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "404", description = "Member not found")
    public ResponseEntity<Member> updateMember(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody Member member) {
        Member updatedMember = memberService.updateMember(id, member, authenticatedUserId(jwt))
            .orElseThrow(() -> new MemberNotFoundException(id));
        return ResponseEntity.ok(updatedMember);
    }

    @DeleteMapping("/members/{id}")
    @Operation(summary = "Delete a member", description = "Deletes a member. Only its group admin may delete members.")
    @ApiResponse(responseCode = "204", description = "Member deleted")
    @ApiResponse(responseCode = "404", description = "Member not found")
    public ResponseEntity<Void> deleteMember(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        if (!memberService.deleteMember(id, authenticatedUserId(jwt))) {
            throw new MemberNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}