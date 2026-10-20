package com.quidoitquoi.api.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.quidoitquoi.api.models.GroupLog;

public interface GroupLogRepository extends JpaRepository<GroupLog, UUID> {
    Page<GroupLog> findByGroup_Id(UUID groupId, Pageable pageable);
}
