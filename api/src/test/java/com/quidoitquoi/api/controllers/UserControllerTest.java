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
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.services.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        UserController userController = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        user = new User(
            "jdoe",
            "john.doe@example.com",
            "https://example.com/john-doe.jpg",
            "Doe",
            "John");
    }

    @Test
    void getAllUsersReturnsUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("jdoe"))
                .andExpect(jsonPath("$[0].email").value("john.doe@example.com"));
    }

    @Test
    void getUserByIdReturnsUser() throws Exception {
        when(userService.getUserById(USER_ID)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jdoe"));
    }

    @Test
    void getUserByIdReturnsNotFoundErrorWhenUserDoesNotExist() throws Exception {
        when(userService.getUserById(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/{id}", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString(USER_ID.toString())));
    }

    @Test
    void getUserByIdReturnsBadRequestForInvalidId() throws Exception {
        mockMvc.perform(get("/api/users/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createUserReturnsCreatedUserAndLocation() throws Exception {
        when(userService.createUser(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/null"))
                .andExpect(jsonPath("$.username").value("jdoe"));
    }

    @Test
    void updateUserReturnsUpdatedUser() throws Exception {
        when(userService.updateUser(eq(USER_ID), any(User.class))).thenReturn(Optional.of(user));

        mockMvc.perform(put("/api/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));
    }

    @Test
    void updateUserReturnsNotFoundErrorWhenUserDoesNotExist() throws Exception {
        when(userService.updateUser(eq(USER_ID), any(User.class))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteUserReturnsNoContent() throws Exception {
        when(userService.deleteUser(USER_ID)).thenReturn(true);

        mockMvc.perform(delete("/api/users/{id}", USER_ID))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(USER_ID);
    }

    @Test
    void deleteUserReturnsNotFoundErrorWhenUserDoesNotExist() throws Exception {
        when(userService.deleteUser(USER_ID)).thenReturn(false);

        mockMvc.perform(delete("/api/users/{id}", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private String userJson() {
        return """
                {
                  "username": "jdoe",
                  "lastname": "Doe",
                  "firstname": "John",
                  "email": "john.doe@example.com",
                  "img": "https://example.com/john-doe.jpg"
                }
                """;
    }
}
