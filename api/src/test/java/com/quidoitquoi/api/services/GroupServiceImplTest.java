package com.quidoitquoi.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.quidoitquoi.api.models.Expense;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.Settlement;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.ExpenseRepository;
import com.quidoitquoi.api.repository.GroupRepository;
import com.quidoitquoi.api.repository.MemberRepository;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.impl.GroupServiceImpl;

class GroupServiceImplTest {

    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ALICE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID BOB_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CAROL_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID ADMIN_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private GroupRepository groupRepository;
    private UserRepository userRepository;
    private MemberRepository memberRepository;
    private ExpenseRepository expenseRepository;
    private GroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        userRepository = mock(UserRepository.class);
        memberRepository = mock(MemberRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        groupService = new GroupServiceImpl(
                groupRepository, userRepository, memberRepository, expenseRepository);
    }

    @Test
    void calculateSettlementsReturnsWhoOwesWho() {
        Member alice = member(ALICE_ID, "Alice");
        Member bob = member(BOB_ID, "Bob");
        Member carol = member(CAROL_ID, "Carol");
        Expense aliceExpense = expense(6000L, alice);
        Expense bobExpense = expense(3000L, bob);

        when(groupRepository.existsById(GROUP_ID)).thenReturn(true);
        when(memberRepository.findByGroup_Id(GROUP_ID)).thenReturn(List.of(alice, bob, carol));
        when(expenseRepository.findByGroup_Id(GROUP_ID)).thenReturn(List.of(aliceExpense, bobExpense));

        List<Settlement> settlements = groupService.calculateSettlements(GROUP_ID);

        assertThat(settlements).containsExactly(
                new Settlement(CAROL_ID, "Carol", ALICE_ID, "Alice", 3000L));
    }

    @Test
    void createGroupAddsAdminAsMember() {
        User admin = mock(User.class);
        Group group = mock(Group.class);
        when(admin.getUsername()).thenReturn("admin");
        when(userRepository.findById(ADMIN_ID)).thenReturn(java.util.Optional.of(admin));
        when(groupRepository.save(group)).thenReturn(group);

        Group createdGroup = groupService.createGroup(ADMIN_ID, group);

        assertThat(createdGroup).isSameAs(group);
        verify(group).setAdmin(admin);
        org.mockito.ArgumentCaptor<Member> memberCaptor = org.mockito.ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getName()).isEqualTo("admin");
        assertThat(memberCaptor.getValue().getGroup()).isSameAs(group);
        assertThat(memberCaptor.getValue().getUser()).isSameAs(admin);
    }

    private Member member(UUID id, String name) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getName()).thenReturn(name);
        return member;
    }

    private Expense expense(Long amount, Member paidBy) {
        Expense expense = mock(Expense.class);
        when(expense.getAmount()).thenReturn(amount);
        when(expense.getPaidBy()).thenReturn(paidBy);
        return expense;
    }
}
