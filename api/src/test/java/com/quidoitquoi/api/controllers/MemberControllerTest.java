package com.quidoitquoi.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.quidoitquoi.api.exceptions.GlobalExceptionHandler;
import com.quidoitquoi.api.exceptions.GroupNotFoundException;
import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.services.MemberService;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MEMBER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private MemberService memberService;

    private MockMvc mockMvc;
    private Member member;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberController(memberService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        User user = new User("jdoe", "john.doe@example.com", null, "Doe", "John");
        Group group = new Group("Trip", "Shared trip expenses", null, "EUR", user);
        member = new Member("John", group);
    }

    @Test
    void addMemberReturnsCreatedMember() throws Exception {
        when(memberService.addMember(eq(GROUP_ID), any(Member.class))).thenReturn(member);

        mockMvc.perform(post("/api/groups/{groupId}/members", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/members/null"))
                .andExpect(jsonPath("$.name").value("John"));
    }

    @Test
    void getMembersByGroupReturnsMembers() throws Exception {
        when(memberService.getMembersByGroupId(GROUP_ID)).thenReturn(List.of(member));

        mockMvc.perform(get("/api/groups/{groupId}/members", GROUP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("John"));
    }

            @Test
            void getMemberByIdReturnsMember() throws Exception {
            when(memberService.getMemberById(MEMBER_ID)).thenReturn(Optional.of(member));

            mockMvc.perform(get("/api/members/{id}", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John"));
            }

            @Test
            void getMemberByIdReturnsNotFoundWhenMemberDoesNotExist() throws Exception {
            when(memberService.getMemberById(MEMBER_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/members/{id}", MEMBER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
            }

            @Test
            void updateMemberReturnsUpdatedMember() throws Exception {
            when(memberService.updateMember(eq(MEMBER_ID), any(Member.class)))
                .thenReturn(Optional.of(member));

            mockMvc.perform(put("/api/members/{id}", MEMBER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(memberJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John"));
            }

            @Test
            void updateMemberReturnsNotFoundWhenMemberDoesNotExist() throws Exception {
            when(memberService.updateMember(eq(MEMBER_ID), any(Member.class)))
                .thenReturn(Optional.empty());

            mockMvc.perform(put("/api/members/{id}", MEMBER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(memberJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
            }

            @Test
            void deleteMemberReturnsNoContent() throws Exception {
            when(memberService.deleteMember(MEMBER_ID)).thenReturn(true);

            mockMvc.perform(delete("/api/members/{id}", MEMBER_ID))
                .andExpect(status().isNoContent());

            verify(memberService).deleteMember(MEMBER_ID);
            }

            @Test
            void deleteMemberReturnsNotFoundWhenMemberDoesNotExist() throws Exception {
            when(memberService.deleteMember(MEMBER_ID)).thenReturn(false);

            mockMvc.perform(delete("/api/members/{id}", MEMBER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
            }

            @Test
            void addGuestMemberDoesNotRequireUser() throws Exception {
            when(memberService.addMember(eq(GROUP_ID), any(Member.class))).thenReturn(member);

            mockMvc.perform(post("/api/groups/{groupId}/members", GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(guestMemberJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John"));
            }

            @Test
            void addMemberReturnsNotFoundWhenGroupDoesNotExist() throws Exception {
            when(memberService.addMember(eq(GROUP_ID), any(Member.class)))
                .thenThrow(new GroupNotFoundException(GROUP_ID));

            mockMvc.perform(post("/api/groups/{groupId}/members", GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(memberJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
            }

            @Test
            void addMemberReturnsNotFoundWhenUserDoesNotExist() throws Exception {
            when(memberService.addMember(eq(GROUP_ID), any(Member.class)))
                .thenThrow(new UserNotFoundException(USER_ID));

            mockMvc.perform(post("/api/groups/{groupId}/members", GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(memberJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
            }

    private String memberJson() {
        return """
                {
                                    "name": "John",
                                    "user": {
                                        "id": "%s"
                                    }
                }
                """.formatted(USER_ID);
    }

        private String guestMemberJson() {
                return """
                                {
                                    "name": "John"
                                }
                                """;
        }
}