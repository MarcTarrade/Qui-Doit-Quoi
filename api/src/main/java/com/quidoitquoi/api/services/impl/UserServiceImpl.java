package com.quidoitquoi.api.services.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.UserService;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;

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
    public Optional<User> updateUser(UUID id, User user) {
        return userRepository.findById(id).map(existingUser -> {
            existingUser.setUsername(user.getUsername());
            existingUser.setLastname(user.getLastname());
            existingUser.setFirstname(user.getFirstname());
            existingUser.setEmail(user.getEmail());
            existingUser.setImg(user.getImg());
            return userRepository.save(existingUser);
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
