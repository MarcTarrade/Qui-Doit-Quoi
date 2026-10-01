package com.quidoitquoi.api.auth;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quidoitquoi.api.exceptions.EmailAlreadyUsedException;

@RestController
@Profile("dev")
@RequestMapping("/api/dev")
public class DevAuthController {

    private final AuthService authService;
    private final String username;
    private final String firstname;
    private final String lastname;
    private final String email;
    private final String password;

    public DevAuthController(
            AuthService authService,
            @Value("${app.dev-user.username}") String username,
            @Value("${app.dev-user.firstname}") String firstname,
            @Value("${app.dev-user.lastname}") String lastname,
            @Value("${app.dev-user.email}") String email,
            @Value("${app.dev-user.password}") String password) {
        this.authService = authService;
        this.username = username;
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;
        this.password = password;
    }

    @PostMapping("/session")
    public ResponseEntity<DevSessionResponse> createSession() {
        AuthResponse authResponse;
        try {
            SignupRequest signupRequest = new SignupRequest(
                    username, lastname, firstname, email, password, null);
            authResponse = authService.signup(signupRequest);
        } catch (EmailAlreadyUsedException exception) {
            authResponse = authService.login(new LoginRequest(email, password));
        }

        return ResponseEntity.ok(new DevSessionResponse(
                authResponse.token(), authResponse.user().getId()));
    }

    public record DevSessionResponse(String token, UUID userId) {
    }
}