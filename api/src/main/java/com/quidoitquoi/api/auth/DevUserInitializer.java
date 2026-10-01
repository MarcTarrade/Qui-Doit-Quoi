package com.quidoitquoi.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.UserRepository;

@Component
@Profile("dev")
public class DevUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String firstname;
    private final String lastname;
    private final String email;
    private final String password;

    public DevUserInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.dev-user.username}") String username,
            @Value("${app.dev-user.firstname}") String firstname,
            @Value("${app.dev-user.lastname}") String lastname,
            @Value("${app.dev-user.email}") String email,
            @Value("${app.dev-user.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = new User(username, email, null, lastname, firstname);
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
    }
}