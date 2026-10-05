package com.quidoitquoi.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtCookieBearerTokenResolverTest {

    private final JwtCookieBearerTokenResolver resolver = new JwtCookieBearerTokenResolver();

    @Test
    void resolvesAuthenticationCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtCookieBearerTokenResolver.COOKIE_NAME, "cookie-token"));

        assertThat(resolver.resolve(request)).isEqualTo("cookie-token");
    }

    @Test
    void prefersBearerHeaderWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");
        request.setCookies(new Cookie(JwtCookieBearerTokenResolver.COOKIE_NAME, "cookie-token"));

        assertThat(resolver.resolve(request)).isEqualTo("header-token");
    }

    @Test
    void returnsNullWhenNoCredentialIsPresent() {
        assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
    }
}