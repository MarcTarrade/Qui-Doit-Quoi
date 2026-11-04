package com.quidoitquoi.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ApiSecurityErrorHandlerTest {

    private ApiSecurityErrorHandler handler;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        handler = new ApiSecurityErrorHandler(messageSource);
    }

    @Test
    void writesFrenchAuthenticationErrorsAsJson() throws Exception {
        MockHttpServletRequest request = frenchRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new BadCredentialsException("Invalid credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains(
                "\"error\":\"Non autorisé\"",
                "\"message\":\"Non autorisé\"",
                "\"path\":\"/api/users/me\"");
    }

    @Test
    void writesFrenchAccessDeniedErrorsAsJson() throws Exception {
        MockHttpServletRequest request = frenchRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(
                "\"error\":\"Accès interdit\"",
                "\"message\":\"Vous n’êtes pas autorisé à effectuer cette opération\"");
    }

    private MockHttpServletRequest frenchRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/users/me");
        request.addHeader("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8");
        return request;
    }
}
