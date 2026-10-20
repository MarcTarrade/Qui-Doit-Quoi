package com.quidoitquoi.api.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupLogResponse(
        UUID id,
        String type,
        Instant date,
        UUID madeById,
        String madeByUsername) {
}
