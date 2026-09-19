package com.quidoitquoi.api.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.quidoitquoi.api.models.Member;

public interface MemberService {
    List<Member> getMembersByGroupId(UUID groupId);

    Optional<Member> getMemberById(UUID id);

    Member addMember(UUID groupId, Member member);

    Optional<Member> updateMember(UUID id, Member member);

    boolean deleteMember(UUID id);
}