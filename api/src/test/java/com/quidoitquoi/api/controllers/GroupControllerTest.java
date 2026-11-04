package com.quidoitquoi.api.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.AfterEach;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.quidoitquoi.api.dto.GroupLogResponse;
import com.quidoitquoi.api.exceptions.GlobalExceptionHandler;
import com.quidoitquoi.api.config.ApiLocaleConfig;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.Settlement;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.services.GroupService;
import com.quidoitquoi.api.services.GroupImageStorageService;
import com.quidoitquoi.api.services.GroupImageStorageService.StoredGroupImage;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    private static final UUID GROUP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID IMAGE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private GroupService groupService;

    @Mock
    private GroupImageStorageService groupImageStorageService;

    private MockMvc mockMvc;
    private Group group;
    private User admin;

    @BeforeEach
    void setUp() {
        GroupController groupController = new GroupController(groupService, groupImageStorageService);
        mockMvc = MockMvcBuilders.standaloneSetup(groupController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setLocaleResolver(new ApiLocaleConfig().localeResolver())
                .build();
        admin = new User(
            "jdoe",
            "john.doe@example.com",
            "https://example.com/john-doe.jpg",
            "Doe",
            "John");
        group = new Group("Trip", "Shared trip expenses", "https://example.com/trip.jpg", "EUR", admin);
        authenticateAsUser();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getGroupsByUserIdReturnsGroups() throws Exception {
        when(groupService.getGroupsByUserId(USER_ID, USER_ID)).thenReturn(List.of(group));

        mockMvc.perform(get("/api/groups/user/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Trip"))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].admin.username").value("jdoe"));
    }

    @Test
    void getGroupByIdReturnsGroup() throws Exception {
        when(groupService.getGroupById(GROUP_ID, USER_ID)).thenReturn(Optional.of(group));

        mockMvc.perform(get("/api/groups/{id}", GROUP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trip"));
    }

    @Test
    void getGroupByIdReturnsNotFoundErrorWhenGroupDoesNotExist() throws Exception {
        when(groupService.getGroupById(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

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
    void getGroupImageReturnsCurrentImageToGroupMember() throws Exception {
        String imageReference = "/api/groups/" + GROUP_ID + "/image/" + IMAGE_ID;
        group.setImg(imageReference);
        when(groupService.getGroupById(GROUP_ID, USER_ID)).thenReturn(Optional.of(group));
        when(groupImageStorageService.imageIdFromReference(GROUP_ID, imageReference))
                .thenReturn(Optional.of(IMAGE_ID));
        when(groupImageStorageService.load(GROUP_ID, IMAGE_ID))
                .thenReturn(Optional.of(new StoredGroupImage(new byte[] { 1, 2, 3 }, MediaType.IMAGE_PNG)));

        mockMvc.perform(get("/api/groups/{id}/image/{imageId}", GROUP_ID, IMAGE_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[] { 1, 2, 3 }))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void uploadGroupImageDelegatesToAdminOnlyService() throws Exception {
        when(groupService.updateGroupImage(eq(GROUP_ID), any(), eq(USER_ID))).thenReturn(group);
        MockMultipartFile image = new MockMultipartFile(
                "image", "trip.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1, 2, 3 });

        mockMvc.perform(multipart("/api/groups/{id}/image", GROUP_ID)
                        .file(image)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trip"));

        verify(groupService).updateGroupImage(eq(GROUP_ID), any(), eq(USER_ID));
    }

    @Test
    void getGroupLogsReturnsPagedLogs() throws Exception {
        GroupLogResponse log = new GroupLogResponse(
                USER_ID, "EXPENSE_ADDED", java.time.Instant.parse("2026-10-20T08:00:00Z"),
                USER_ID, "jdoe");
        when(groupService.getGroupLogs(GROUP_ID, 1, 10, USER_ID))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/groups/{id}/logs", GROUP_ID)
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.content[0].type").value("EXPENSE_ADDED"))
                .andExpect(jsonPath("$.content[0].madeByUsername").value("jdoe"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11));

        verify(groupService).getGroupLogs(GROUP_ID, 1, 10, USER_ID);
    }

        @Test
        void getSettlementsReturnsTransfers() throws Exception {
        when(groupService.calculateSettlements(GROUP_ID, USER_ID)).thenReturn(List.of(
            new Settlement(USER_ID, "John", GROUP_ID, "Jane", 3000L)));

        mockMvc.perform(get("/api/groups/{id}/settlements", GROUP_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fromMemberName").value("John"))
            .andExpect(jsonPath("$[0].toMemberName").value("Jane"))
            .andExpect(jsonPath("$[0].amount").value(3000));
        }

    @Test
    void createGroupReturnsCreatedGroupAndLocation() throws Exception {
        when(groupService.createGroup(eq(USER_ID), any(Group.class), eq(USER_ID))).thenReturn(group);

        mockMvc.perform(post("/api/groups/{userId}", USER_ID)
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/groups/null"))
                .andExpect(jsonPath("$.name").value("Trip"))
                .andExpect(jsonPath("$.admin.username").value("jdoe"));
    }

    @Test
    void createGroupRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/groups/{userId}", USER_ID)
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "description": "Shared trip expenses",
                                  "img": "https://example.com/trip.jpg",
                                  "currency": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createGroupReturnsFrenchValidationErrorsWhenRequested() throws Exception {
        mockMvc.perform(post("/api/groups/{userId}", USER_ID)
                        .header("Accept-Language", "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "description": "Shared trip expenses",
                                  "img": "https://example.com/trip.jpg",
                                  "currency": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Le nom du groupe est obligatoire")))
                .andExpect(jsonPath("$.message").value(containsString("La devise est obligatoire")));
    }

    @Test
    void updateGroupReturnsUpdatedGroup() throws Exception {
        when(groupService.updateGroup(eq(GROUP_ID), any(Group.class), eq(USER_ID))).thenReturn(Optional.of(group));
        authenticateAsUser();

        mockMvc.perform(put("/api/groups/{id}", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void updateGroupReturnsNotFoundErrorWhenGroupDoesNotExist() throws Exception {
        when(groupService.updateGroup(eq(GROUP_ID), any(Group.class), eq(USER_ID))).thenReturn(Optional.empty());
        authenticateAsUser();

        mockMvc.perform(put("/api/groups/{id}", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private void authenticateAsUser() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void deleteGroupReturnsNoContent() throws Exception {
        when(groupService.deleteGroup(GROUP_ID, USER_ID)).thenReturn(true);

        mockMvc.perform(delete("/api/groups/{id}", GROUP_ID))
                .andExpect(status().isNoContent());

        verify(groupService).deleteGroup(GROUP_ID, USER_ID);
    }

    @Test
    void deleteGroupReturnsNotFoundErrorWhenGroupDoesNotExist() throws Exception {
        when(groupService.deleteGroup(GROUP_ID, USER_ID)).thenReturn(false);

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
