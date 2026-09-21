package com.quidoitquoi.api.controllers;

import static org.hamcrest.Matchers.containsString;
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
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.services.GroupService;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private GroupService groupService;

    private MockMvc mockMvc;
    private Group group;
    private User admin;

    @BeforeEach
    void setUp() {
        GroupController groupController = new GroupController(groupService);
        mockMvc = MockMvcBuilders.standaloneSetup(groupController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        admin = new User(
            "jdoe",
            "john.doe@example.com",
            "https://example.com/john-doe.jpg",
            "Doe",
            "John");
        group = new Group("Trip", "Shared trip expenses", "https://example.com/trip.jpg", "EUR", admin);
    }

    @Test
    void getGroupsByUserIdReturnsGroups() throws Exception {
        when(groupService.getGroupsByUserId(USER_ID)).thenReturn(List.of(group));

        mockMvc.perform(get("/api/groups/user/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Trip"))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].admin.username").value("jdoe"));
    }

    @Test
    void getGroupByIdReturnsGroup() throws Exception {
        when(groupService.getGroupById(GROUP_ID)).thenReturn(Optional.of(group));

        mockMvc.perform(get("/api/groups/{id}", GROUP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trip"));
    }

    @Test
    void getGroupByIdReturnsNotFoundErrorWhenGroupDoesNotExist() throws Exception {
        when(groupService.getGroupById(GROUP_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/groups/{id}", GROUP_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString(GROUP_ID.toString())));
    }

    @Test
    void getGroupByIdReturnsBadRequestForInvalidId() throws Exception {
        mockMvc.perform(get("/api/groups/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createGroupReturnsCreatedGroupAndLocation() throws Exception {
        when(groupService.createGroup(eq(USER_ID), any(Group.class))).thenReturn(group);

        mockMvc.perform(post("/api/groups/{userId}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/groups/null"))
                .andExpect(jsonPath("$.name").value("Trip"))
                .andExpect(jsonPath("$.admin.username").value("jdoe"));
    }

    @Test
    void updateGroupReturnsUpdatedGroup() throws Exception {
        when(groupService.updateGroup(eq(GROUP_ID), any(Group.class))).thenReturn(Optional.of(group));

        mockMvc.perform(put("/api/groups/{id}", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void updateGroupReturnsNotFoundErrorWhenGroupDoesNotExist() throws Exception {
        when(groupService.updateGroup(eq(GROUP_ID), any(Group.class))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/groups/{id}", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteGroupReturnsNoContent() throws Exception {
        when(groupService.deleteGroup(GROUP_ID)).thenReturn(true);

        mockMvc.perform(delete("/api/groups/{id}", GROUP_ID))
                .andExpect(status().isNoContent());

        verify(groupService).deleteGroup(GROUP_ID);
    }

    @Test
    void deleteGroupReturnsNotFoundErrorWhenGroupDoesNotExist() throws Exception {
        when(groupService.deleteGroup(GROUP_ID)).thenReturn(false);

        mockMvc.perform(delete("/api/groups/{id}", GROUP_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private String groupJson() {
        return """
                {
                  "name": "Trip",
                  "description": "Shared trip expenses",
                  "img": "https://example.com/trip.jpg",
                  "currency": "EUR"
                }
                """;
    }
}
