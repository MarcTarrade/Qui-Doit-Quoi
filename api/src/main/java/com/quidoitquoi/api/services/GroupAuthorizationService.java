package com.quidoitquoi.api.services;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.quidoitquoi.api.exceptions.GroupNotFoundException;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.repository.GroupRepository;
import com.quidoitquoi.api.repository.MemberRepository;

@Service
public class GroupAuthorizationService {

    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;

    public GroupAuthorizationService(
            GroupRepository groupRepository,
            MemberRepository memberRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
    }

    public Group requireMember(UUID groupId, UUID userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        if (!isMember(group, userId)) {
            throw new GroupNotFoundException(groupId);
        }
        return group;
    }

    public Group requireAdmin(UUID groupId, UUID userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        if (!isGroupAdmin(group, userId)) {
            throw new GroupNotFoundException(groupId);
        }
        return group;
    }

    public boolean isMember(Group group, UUID userId) {
        return isGroupAdmin(group, userId)
                || memberRepository.existsByGroup_IdAndUser_Id(group.getId(), userId);
    }

    public boolean isGroupAdmin(Group group, UUID userId) {
        return group.getAdmin() != null && group.getAdmin().getId().equals(userId);
    }
}
