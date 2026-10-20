package com.quidoitquoi.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.quidoitquoi.api.dto.GroupLogResponse;
import com.quidoitquoi.api.models.Expense;
import com.quidoitquoi.api.models.GroupLog;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.Settlement;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.ExpenseRepository;
import com.quidoitquoi.api.repository.GroupLogRepository;
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
    private GroupLogRepository groupLogRepository;
    private UserRepository userRepository;
    private MemberRepository memberRepository;
    private ExpenseRepository expenseRepository;
    private GroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        groupLogRepository = mock(GroupLogRepository.class);
        userRepository = mock(UserRepository.class);
        memberRepository = mock(MemberRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        groupService = new GroupServiceImpl(
                groupRepository, groupLogRepository, userRepository, memberRepository, expenseRepository);
    }

    @Test
    void getGroupsByUserIdReturnsGroupsAdministeredOrJoinedByUser() {
        Group administeredGroup = mock(Group.class);
        Group joinedGroup = mock(Group.class);
        when(userRepository.findById(ALICE_ID)).thenReturn(java.util.Optional.of(mock(User.class)));
        when(groupRepository.findDistinctByAdmin_IdOrMembers_User_Id(ALICE_ID, ALICE_ID))
                .thenReturn(List.of(administeredGroup, joinedGroup));

        List<Group> groups = groupService.getGroupsByUserId(ALICE_ID);

        assertThat(groups).containsExactly(administeredGroup, joinedGroup);
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
    void getGroupLogsReturnsPagedActivityAndCapsPageSizeAtTen() {
        GroupLog log = mock(GroupLog.class);
        User madeBy = mock(User.class);
        when(groupRepository.existsById(GROUP_ID)).thenReturn(true);
        when(groupLogRepository.findByGroup_Id(
                org.mockito.ArgumentMatchers.eq(GROUP_ID),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(log.getId()).thenReturn(ALICE_ID);
        when(log.getType()).thenReturn(GroupLog.EXPENSE_ADDED);
        when(log.getMadeBy()).thenReturn(madeBy);
        when(madeBy.getId()).thenReturn(ADMIN_ID);
        when(madeBy.getUsername()).thenReturn("admin");

        var result = groupService.getGroupLogs(GROUP_ID, 2, 25);

        assertThat(result.getContent()).containsExactly(new GroupLogResponse(
                ALICE_ID,
                GroupLog.EXPENSE_ADDED,
                null,
                ADMIN_ID,
                "admin"));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(groupLogRepository).findByGroup_Id(
                org.mockito.ArgumentMatchers.eq(GROUP_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("date").getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
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

    @Test
    void updateGroupPreservesExistingAdmin() {
        Group existingGroup = mock(Group.class);
        Group updatedDetails = mock(Group.class);
        when(groupRepository.findById(GROUP_ID)).thenReturn(java.util.Optional.of(existingGroup));
        when(groupRepository.save(existingGroup)).thenReturn(existingGroup);

        Group result = groupService.updateGroup(GROUP_ID, updatedDetails).orElseThrow();

        assertThat(result).isSameAs(existingGroup);
        verify(existingGroup).setName(updatedDetails.getName());
        verify(existingGroup).setDescription(updatedDetails.getDescription());
        verify(existingGroup).setImg(updatedDetails.getImg());
        verify(existingGroup).setCurrency(updatedDetails.getCurrency());
        verify(existingGroup, never()).setAdmin(any(User.class));
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
