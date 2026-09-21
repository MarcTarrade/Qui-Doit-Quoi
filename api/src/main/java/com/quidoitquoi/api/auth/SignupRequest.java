package com.quidoitquoi.api.auth;

public record SignupRequest(
        String username,
        String lastname,
        String firstname,
        String email,
        String password,
        String img) {
}
