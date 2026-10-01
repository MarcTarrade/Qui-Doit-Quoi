package com.quidoitquoi.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DevUserInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DevUserInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DevUserInitializer(
                userRepository,
                passwordEncoder,
                "frontend-dev",
                "Frontend",
                "Developer",
                "frontend-dev@qui-doit-quoi.local",
                "frontend-dev-password");
    }

    @Test
    void createsConfiguredUserAtStartupWithEncodedPassword() {
        when(userRepository.existsByEmail("frontend-dev@qui-doit-quoi.local")).thenReturn(false);
        when(passwordEncoder.encode("frontend-dev-password")).thenReturn("encoded-password");

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("frontend-dev");
        assertThat(savedUser.getFirstname()).isEqualTo("Frontend");
        assertThat(savedUser.getLastname()).isEqualTo("Developer");
        assertThat(savedUser.getEmail()).isEqualTo("frontend-dev@qui-doit-quoi.local");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    void leavesExistingConfiguredUserUnchanged() {
        when(userRepository.existsByEmail("frontend-dev@qui-doit-quoi.local")).thenReturn(true);

        initializer.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }
}