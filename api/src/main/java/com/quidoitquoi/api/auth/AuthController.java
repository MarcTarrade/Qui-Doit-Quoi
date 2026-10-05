package com.quidoitquoi.api.auth;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Account registration, login, and CSRF token endpoints")
public class AuthController {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private final AuthService authService;
    @Value("${security.jwt.cookie.secure:true}")
    private boolean secureCookie = true;
    @Value("${security.jwt.cookie.same-site:Lax}")
    private String sameSite = "Lax";

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF token", description = "Returns the CSRF token and the request header name expected by protected state-changing requests.")
    @ApiResponse(responseCode = "200", description = "CSRF token returned")
    public Map<String, String> csrfToken(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName());
    }

    @PostMapping("/signup")
    @Operation(summary = "Create an account", description = "Registers a new user and sets an HttpOnly authentication cookie.")
    @SecurityRequirement(name = "csrfToken")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "409", description = "An account with the supplied identity already exists")
    public ResponseEntity<AuthUserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return authenticatedResponse(HttpStatus.CREATED, authService.signup(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Authenticates a user and sets an HttpOnly authentication cookie.")
    @SecurityRequirement(name = "csrfToken")
    @ApiResponse(responseCode = "200", description = "Authentication successful")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<AuthUserResponse> login(@Valid @RequestBody LoginRequest request) {
        return authenticatedResponse(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Clears the authentication cookie.")
    @SecurityRequirement(name = "csrfToken")
    @ApiResponse(responseCode = "200", description = "Authentication cookie cleared")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authenticationCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<AuthUserResponse> authenticatedResponse(HttpStatus status, AuthResponse auth) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, authenticationCookie(auth.token(), TOKEN_LIFETIME).toString())
                .body(new AuthUserResponse(auth.user()));
    }

    private ResponseCookie authenticationCookie(String token, Duration maxAge) {
        return ResponseCookie.from(JwtCookieBearerTokenResolver.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
