package com.quidoitquoi.api.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.quidoitquoi.api.models.User;

@Service 
public interface UserService {
    List<User> getAllUsers();

    Optional<User> getUserById(UUID id);

    User createUser(User user);

    Optional<User> updateUser(UUID id, User user);

    Optional<User> updateUserImage(UUID id, MultipartFile image);

    Optional<User> deleteUserImage(UUID id);

    boolean deleteUser(UUID id);
}
