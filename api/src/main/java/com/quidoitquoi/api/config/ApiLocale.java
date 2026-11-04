package com.quidoitquoi.api.config;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

public final class ApiLocale {

    private ApiLocale() {
    }

    public static Locale fromRequest(HttpServletRequest request) {
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.ENGLISH;
        }

        for (String languageRange : acceptLanguage.split(",")) {
            String languageTag = languageRange.split(";", 2)[0].trim();
            String language = Locale.forLanguageTag(languageTag).getLanguage();
            if (Locale.FRENCH.getLanguage().equals(language)) {
                return Locale.FRENCH;
            }
            if (Locale.ENGLISH.getLanguage().equals(language)) {
                return Locale.ENGLISH;
            }
        }

        return Locale.ENGLISH;
    }
}
