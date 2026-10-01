package com.quidoitquoi.api.auth;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
@RequestMapping("/api/dev")
public class DevAuthController {

    private final AuthService authService;
    private final String email;
    private final String password;

    public DevAuthController(
            AuthService authService,
            @Value("${app.dev-user.email}") String email,
            @Value("${app.dev-user.password}") String password) {
        this.authService = authService;
        this.email = email;
        this.password = password;
    }

    @PostMapping("/session")
    public ResponseEntity<DevSessionResponse> createSession() {
        AuthResponse authResponse = authService.login(new LoginRequest(email, password));

        return ResponseEntity.ok(new DevSessionResponse(
                authResponse.token(), authResponse.user().getId()));
    }

    public record DevSessionResponse(String token, UUID userId) {
    }
}