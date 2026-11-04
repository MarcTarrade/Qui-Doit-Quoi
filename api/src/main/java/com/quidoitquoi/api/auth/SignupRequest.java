package com.quidoitquoi.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "{validation.username.required}")
        @Size(max = 50, message = "{validation.username.max}")
        String username,

        @NotBlank(message = "{validation.lastname.required}")
        @Size(max = 100, message = "{validation.lastname.max}")
        String lastname,

        @NotBlank(message = "{validation.firstname.required}")
        @Size(max = 100, message = "{validation.firstname.max}")
        String firstname,

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, message = "{validation.password.min}")
        String password,

        @Size(max = 2048, message = "{validation.image-url.max}")
        String img) {
}
