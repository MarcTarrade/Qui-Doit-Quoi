package com.quidoitquoi.api.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quidoitquoi.api.models.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    List<Expense> findByGroup_Id(UUID groupId);
}
