package com.quidoitquoi.api.services.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.exceptions.MemberNotFoundException;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.MemberRepository;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.GroupAuthorizationService;
import com.quidoitquoi.api.services.MemberService;

@Service
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final GroupAuthorizationService groupAuthorizationService;

    public MemberServiceImpl(
            MemberRepository memberRepository,
            UserRepository userRepository,
            GroupAuthorizationService groupAuthorizationService) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.groupAuthorizationService = groupAuthorizationService;
    }

    @Override
    public List<Member> getMembersByGroupId(UUID groupId, UUID authenticatedUserId) {
        groupAuthorizationService.requireMember(groupId, authenticatedUserId);
        return memberRepository.findByGroup_Id(groupId);
    }

    @Override
    public Optional<Member> getMemberById(UUID id, UUID authenticatedUserId) {
        return memberRepository.findById(id).map(member -> {
            if (!groupAuthorizationService.isMember(member.getGroup(), authenticatedUserId)) {
                throw new MemberNotFoundException(id);
            }
            return member;
        });
    }

    @Override
    public Member addMember(UUID groupId, Member member, UUID authenticatedUserId) {
        member.setGroup(groupAuthorizationService.requireAdmin(groupId, authenticatedUserId));
        return memberRepository.save(member);
    }

    @Override
    public Optional<Member> updateMember(UUID id, Member member, UUID authenticatedUserId) {
        return memberRepository.findById(id).map(existingMember -> {
            if (!groupAuthorizationService.isGroupAdmin(existingMember.getGroup(), authenticatedUserId)) {
                throw new MemberNotFoundException(id);
            }
            existingMember.setName(member.getName());
            existingMember.setUser(resolveUser(member));
            return memberRepository.save(existingMember);
        });
    }

    @Override
    public boolean deleteMember(UUID id, UUID authenticatedUserId) {
        Optional<Member> member = memberRepository.findById(id);
        if (member.isEmpty()) {
            return false;
        }
        if (!groupAuthorizationService.isGroupAdmin(member.get().getGroup(), authenticatedUserId)) {
            throw new MemberNotFoundException(id);
        }
        memberRepository.delete(member.get());
        return true;
    }

    private User resolveUser(Member member) {
        if (member.getUser() == null || member.getUser().getId() == null) {
            return null;
        }

        UUID userId = member.getUser().getId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}