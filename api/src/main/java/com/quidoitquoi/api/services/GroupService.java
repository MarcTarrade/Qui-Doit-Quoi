package com.quidoitquoi.api.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.quidoitquoi.api.dto.GroupLogResponse;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.Settlement;

@Service
public interface GroupService {
    int MAX_LOG_PAGE_SIZE = 10;

    List<Group> getGroupsByUserId(UUID userId, UUID authenticatedUserId);

    Optional<Group> getGroupById(UUID id, UUID authenticatedUserId);

    Page<GroupLogResponse> getGroupLogs(UUID groupId, int page, int size, UUID authenticatedUserId);

    List<Settlement> calculateSettlements(UUID groupId, UUID authenticatedUserId);

    Group createGroup(UUID userId, Group group, UUID authenticatedUserId);

    Optional<Group> updateGroup(UUID id, Group group, UUID madeByUserId);

    boolean deleteGroup(UUID id, UUID authenticatedUserId);
}