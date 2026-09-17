package com.quidoitquoi.api.services.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.repository.GroupRepository;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.GroupService;

@Service
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public GroupServiceImpl(GroupRepository groupRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<Group> getGroupsByUserId(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return groupRepository.findByAdmin_Id(userId);
    }

    @Override
    public Optional<Group> getGroupById(UUID id) {
        return groupRepository.findById(id);
    }

    @Override
    public Group createGroup(UUID userId, Group group) {
        group.setAdmin(userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId)));
        return groupRepository.save(group);
    }

    @Override
    public Optional<Group> updateGroup(UUID id, Group group) {
        return groupRepository.findById(id).map(existingGroup -> {
            existingGroup.setName(group.getName());
            existingGroup.setDescription(group.getDescription());
            existingGroup.setImg(group.getImg());
            existingGroup.setCurrency(group.getCurrency());
            existingGroup.setAdmin(group.getAdmin());
            return groupRepository.save(existingGroup);
        });
    }

    @Override
    public boolean deleteGroup(UUID id) {
        if (!groupRepository.existsById(id)) {
            return false;
        }

        groupRepository.deleteById(id);
        return true;
    }
}