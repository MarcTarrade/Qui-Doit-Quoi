package com.quidoitquoi.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtEncoder jwtEncoder;

    private AuthServiceImpl authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, passwordEncoder, authenticationManager, jwtEncoder);
        user = new User("jdoe", "john.doe@example.com", null, "Doe", "John");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt("token-value"));
    }

    @Test
    void signupHashesPasswordAndReturnsToken() {
        SignupRequest request = new SignupRequest(
                "jdoe", "Doe", "John", "john.doe@example.com", "plain-password", null);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            setId(saved, USER_ID);
            return saved;
        });

        AuthResponse response = authService.signup(request);

        assertThat(response.token()).isEqualTo("token-value");
        assertThat(response.user().getPasswordHash()).isEqualTo("hashed-password");
        verify(passwordEncoder).encode("plain-password");
    }

    @Test
    void loginAuthenticatesCredentialsAndReturnsToken() {
        LoginRequest request = new LoginRequest("john.doe@example.com", "plain-password");
        setId(user, USER_ID);
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("token-value");
        verify(authenticationManager).authenticate(any());
    }

    private Jwt jwt(String token) {
        return Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .claim("sub", USER_ID.toString())
                .build();
    }

    private void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
