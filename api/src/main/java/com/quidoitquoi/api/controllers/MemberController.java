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

import com.quidoitquoi.api.exceptions.MemberNotFoundException;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.services.MemberService;

@RestController
@RequestMapping("/api")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/groups/{groupId}/members")
    public ResponseEntity<List<Member>> getMembersByGroupId(@PathVariable UUID groupId) {
        return ResponseEntity.ok(memberService.getMembersByGroupId(groupId));
    }

    @GetMapping("/members/{id}")
    public ResponseEntity<Member> getMemberById(@PathVariable UUID id) {
        Member member = memberService.getMemberById(id)
            .orElseThrow(() -> new MemberNotFoundException(id));
        return ResponseEntity.ok(member);
    }

    @PostMapping("/groups/{groupId}/members")
    public ResponseEntity<Member> addMember(
            @PathVariable UUID groupId,
            @RequestBody Member member) {
        Member createdMember = memberService.addMember(groupId, member);
        return ResponseEntity.created(URI.create("/api/members/" + createdMember.getId()))
                .body(createdMember);
    }

    @PutMapping("/members/{id}")
    public ResponseEntity<Member> updateMember(
            @PathVariable UUID id,
            @RequestBody Member member) {
        Member updatedMember = memberService.updateMember(id, member)
            .orElseThrow(() -> new MemberNotFoundException(id));
        return ResponseEntity.ok(updatedMember);
    }

    @DeleteMapping("/members/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable UUID id) {
        if (!memberService.deleteMember(id)) {
            throw new MemberNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}