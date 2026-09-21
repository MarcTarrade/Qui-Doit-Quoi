package com.quidoitquoi.api.models;

import java.util.UUID;

public record Settlement(
        UUID fromMemberId,
        String fromMemberName,
        UUID toMemberId,
        String toMemberName,
        Long amount) {
}
