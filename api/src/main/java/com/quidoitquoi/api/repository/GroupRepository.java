package com.quidoitquoi.api.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quidoitquoi.api.models.Group;

public interface GroupRepository extends JpaRepository<Group, UUID> {
	List<Group> findByAdmin_Id(UUID adminId);
}