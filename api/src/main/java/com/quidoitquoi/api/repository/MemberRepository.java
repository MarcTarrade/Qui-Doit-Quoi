package com.quidoitquoi.api.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quidoitquoi.api.models.Member;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    List<Member> findByGroup_Id(UUID groupId);
}