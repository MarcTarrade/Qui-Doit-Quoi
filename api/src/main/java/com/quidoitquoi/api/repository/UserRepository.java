package com.quidoitquoi.api.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quidoitquoi.api.models.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    
}
