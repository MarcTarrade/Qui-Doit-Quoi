package com.quidoitquoi.api.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.quidoitquoi.api.models.Group;

@Service
public interface GroupService {
    List<Group> getGroupsByUserId(UUID userId);

    Optional<Group> getGroupById(UUID id);

    Group createGroup(UUID userId, Group group);

    Optional<Group> updateGroup(UUID id, Group group);

    boolean deleteGroup(UUID id);
}