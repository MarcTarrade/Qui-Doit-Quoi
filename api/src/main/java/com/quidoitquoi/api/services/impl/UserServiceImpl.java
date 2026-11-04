package com.quidoitquoi.api.services.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.GroupImageStorageService;
import com.quidoitquoi.api.services.UserService;

@Service
public class UserServiceImpl implements UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final GroupImageStorageService imageStorageService;

    public UserServiceImpl(UserRepository userRepository, GroupImageStorageService imageStorageService) {
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
    }

    @Override
    public List<User> getAllUsers() {
        return (List<User>) userRepository.findAll();
    }

    @Override
    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }

    @Override
    public User createUser(User user) {
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public Optional<User> updateUser(UUID id, User user) {
        return userRepository.findById(id).map(existingUser -> {
            existingUser.setUsername(user.getUsername());
            existingUser.setLastname(user.getLastname());
            existingUser.setFirstname(user.getFirstname());
            existingUser.setEmail(user.getEmail());
            if (user.getImg() != null) {
                existingUser.setImg(user.getImg());
            }
            return userRepository.save(existingUser);
        });
    }

    @Override
    @Transactional
    public Optional<User> updateUserImage(UUID id, MultipartFile image) {
        return userRepository.findById(id).map(user -> {
            UUID newImageId = imageStorageService.storeUserImage(id, image);
            Optional<UUID> previousImageId =
                    imageStorageService.userImageIdFromReference(id, user.getImg());
            user.setImg(imageStorageService.userImageReference(id, newImageId));
            registerImageCleanup(id, previousImageId, newImageId);
            return userRepository.save(user);
        });
    }

    @Override
    @Transactional
    public Optional<User> deleteUserImage(UUID id) {
        return userRepository.findById(id).map(user -> {
            Optional<UUID> previousImageId =
                    imageStorageService.userImageIdFromReference(id, user.getImg());
            user.setImg(null);
            registerImageCleanup(id, previousImageId, null);
            return userRepository.save(user);
        });
    }

    private void registerImageCleanup(UUID userId, Optional<UUID> previousImageId, UUID newImageId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                UUID imageToDelete = status == STATUS_COMMITTED
                        ? previousImageId.orElse(null)
                        : newImageId;
                if (imageToDelete == null) {
                    return;
                }
                try {
                    imageStorageService.deleteUserImage(userId, imageToDelete);
                } catch (IOException exception) {
                    logger.error("Unable to clean up profile image {} for user {}", imageToDelete, userId, exception);
                }
            }
        });
    }

    @Override
    public boolean deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            return false;
        }

        userRepository.deleteById(id);
        return true;
    }
}
