package com.quidoitquoi.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.quidoitquoi.api.exceptions.GroupNotFoundException;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.GroupRepository;
import com.quidoitquoi.api.repository.MemberRepository;

class GroupAuthorizationServiceTest {

    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MEMBER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID NON_MEMBER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private GroupRepository groupRepository;
    private MemberRepository memberRepository;
    private GroupAuthorizationService authorizationService;
    private Group group;
    private User admin;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        memberRepository = mock(MemberRepository.class);
        authorizationService = new GroupAuthorizationService(groupRepository, memberRepository);

        admin = mock(User.class);
        group = mock(Group.class);
        when(group.getId()).thenReturn(GROUP_ID);
        when(group.getAdmin()).thenReturn(admin);
        when(admin.getId()).thenReturn(ADMIN_ID);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
    }

    @Test
    void allowsAnAuthenticatedGroupMemberToReadTheGroup() {
        when(memberRepository.existsByGroup_IdAndUser_Id(GROUP_ID, MEMBER_ID)).thenReturn(true);

        assertThat(authorizationService.requireMember(GROUP_ID, MEMBER_ID)).isSameAs(group);
    }

    @Test
    void hidesGroupFromUsersWhoAreNotMembers() {
        when(memberRepository.existsByGroup_IdAndUser_Id(GROUP_ID, NON_MEMBER_ID)).thenReturn(false);

        assertThatThrownBy(() -> authorizationService.requireMember(GROUP_ID, NON_MEMBER_ID))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void allowsOnlyTheGroupAdminToManageMembersAndSettings() {
        assertThat(authorizationService.requireAdmin(GROUP_ID, ADMIN_ID)).isSameAs(group);

        assertThatThrownBy(() -> authorizationService.requireAdmin(GROUP_ID, MEMBER_ID))
                .isInstanceOf(GroupNotFoundException.class);
    }
}
