package com.quidoitquoi.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.quidoitquoi.api.exceptions.EmailAlreadyUsedException;
import com.quidoitquoi.api.models.User;

class DevAuthControllerTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private AuthService authService;
    private DevAuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        controller = new DevAuthController(
                authService,
                "frontend-dev",
                "Frontend",
                "Developer",
                "frontend-dev@qui-doit-quoi.local",
                "frontend-dev-password");
    }

    @Test
    void createsTheDevelopmentUserOnFirstSessionRequest() {
                AuthResponse authResponse = authResponse();
                when(authService.signup(any(SignupRequest.class))).thenReturn(authResponse);

        var response = controller.createSession();

        assertThat(response.getBody()).isEqualTo(
                new DevAuthController.DevSessionResponse("dev-token", USER_ID));
    }

    @Test
    void logsInTheDevelopmentUserWhenItAlreadyExists() {
                AuthResponse authResponse = authResponse();
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new EmailAlreadyUsedException("frontend-dev@qui-doit-quoi.local"));
        when(authService.login(new LoginRequest(
                "frontend-dev@qui-doit-quoi.local", "frontend-dev-password")))
                .thenReturn(authResponse);

        var response = controller.createSession();

        assertThat(response.getBody()).isEqualTo(
                new DevAuthController.DevSessionResponse("dev-token", USER_ID));
        verify(authService).login(new LoginRequest(
                "frontend-dev@qui-doit-quoi.local", "frontend-dev-password"));
    }

    private AuthResponse authResponse() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        return new AuthResponse("dev-token", user);
    }
}