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

    List<Group> getGroupsByUserId(UUID userId);

    Optional<Group> getGroupById(UUID id);

    Page<GroupLogResponse> getGroupLogs(UUID groupId, int page, int size);

    List<Settlement> calculateSettlements(UUID groupId);

    Group createGroup(UUID userId, Group group);

    Optional<Group> updateGroup(UUID id, Group group);

    boolean deleteGroup(UUID id);
}