package com.quidoitquoi.api.services.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.quidoitquoi.api.exceptions.GroupNotFoundException;
import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.GroupRepository;
import com.quidoitquoi.api.repository.MemberRepository;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.MemberService;

@Service
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public MemberServiceImpl(
            MemberRepository memberRepository,
            GroupRepository groupRepository,
            UserRepository userRepository) {
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<Member> getMembersByGroupId(UUID groupId) {
        ensureGroupExists(groupId);
        return memberRepository.findByGroup_Id(groupId);
    }

    @Override
    public Optional<Member> getMemberById(UUID id) {
        return memberRepository.findById(id);
    }

    @Override
    public Member addMember(UUID groupId, Member member) {
        member.setGroup(groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId)));
        return memberRepository.save(member);
    }

    @Override
    public Optional<Member> updateMember(UUID id, Member member) {
        return memberRepository.findById(id).map(existingMember -> {
            existingMember.setName(member.getName());
            existingMember.setUser(resolveUser(member));
            return memberRepository.save(existingMember);
        });
    }

    @Override
    public boolean deleteMember(UUID id) {
        if (!memberRepository.existsById(id)) {
            return false;
        }

        memberRepository.deleteById(id);
        return true;
    }

    private void ensureGroupExists(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
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