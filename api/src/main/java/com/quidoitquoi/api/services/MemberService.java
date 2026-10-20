package com.quidoitquoi.api.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.quidoitquoi.api.models.Member;

public interface MemberService {
    List<Member> getMembersByGroupId(UUID groupId, UUID authenticatedUserId);

    Optional<Member> getMemberById(UUID id, UUID authenticatedUserId);

    Member addMember(UUID groupId, Member member, UUID authenticatedUserId);

    Optional<Member> updateMember(UUID id, Member member, UUID authenticatedUserId);

    boolean deleteMember(UUID id, UUID authenticatedUserId);
}