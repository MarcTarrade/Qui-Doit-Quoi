package com.quidoitquoi.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.quidoitquoi.api.models.Category;
import com.quidoitquoi.api.models.Expense;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.GroupLog;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.ExpenseRepository;
import com.quidoitquoi.api.repository.GroupLogRepository;
import com.quidoitquoi.api.repository.GroupRepository;
import com.quidoitquoi.api.repository.MemberRepository;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.impl.ExpenseServiceImpl;

class ExpenseServiceImplTest {

    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MEMBER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private ExpenseRepository expenseRepository;
    private GroupLogRepository groupLogRepository;
    private GroupRepository groupRepository;
    private MemberRepository memberRepository;
    private UserRepository userRepository;
    private ExpenseServiceImpl expenseService;

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        groupLogRepository = mock(GroupLogRepository.class);
        groupRepository = mock(GroupRepository.class);
        memberRepository = mock(MemberRepository.class);
        userRepository = mock(UserRepository.class);
        expenseService = new ExpenseServiceImpl(
                expenseRepository,
                groupLogRepository,
                groupRepository,
                memberRepository,
                userRepository);
    }

    @Test
    void addExpensePersistsAnActivityLogForTheAuthenticatedUser() {
        Group group = mock(Group.class);
        Member payer = mock(Member.class);
        User madeBy = mock(User.class);
        Expense expense = new Expense("Dinner", 4500L, Category.FOOD, payer, null);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(payer));
        when(payer.getId()).thenReturn(MEMBER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(madeBy));
        when(expenseRepository.save(expense)).thenReturn(expense);

        Expense result = expenseService.addExpense(GROUP_ID, expense, USER_ID);

        assertThat(result).isSameAs(expense);
        assertThat(expense.getGroup()).isSameAs(group);
        assertThat(expense.getPaidBy()).isSameAs(payer);
        ArgumentCaptor<GroupLog> logCaptor = ArgumentCaptor.forClass(GroupLog.class);
        verify(groupLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getType()).isEqualTo(GroupLog.EXPENSE_ADDED);
        assertThat(logCaptor.getValue().getGroup()).isSameAs(group);
        assertThat(logCaptor.getValue().getMadeBy()).isSameAs(madeBy);

        InOrder persistenceOrder = inOrder(expenseRepository, groupLogRepository);
        persistenceOrder.verify(expenseRepository).save(expense);
        persistenceOrder.verify(groupLogRepository).save(any(GroupLog.class));
    }
}
