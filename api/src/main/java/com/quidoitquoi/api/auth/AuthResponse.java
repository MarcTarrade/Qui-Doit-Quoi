package com.quidoitquoi.api.auth;

import com.quidoitquoi.api.models.User;

public record AuthResponse(String token, User user) {
}
