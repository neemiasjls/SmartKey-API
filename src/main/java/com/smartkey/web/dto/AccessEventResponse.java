package com.smartkey.web.dto;

import com.smartkey.domain.enums.AccessDecision;
import com.smartkey.domain.enums.AccessDenyReason;
import com.smartkey.domain.model.AccessEvent;

import java.time.Instant;
import java.util.UUID;

public record AccessEventResponse(
        UUID id,
        String readerCode,
        String accessPointCode,
        UUID credentialId,
        AccessDecision decision,
        AccessDenyReason reason,
        String message,
        Instant occurredAt
) {
    public static AccessEventResponse from(AccessEvent e) {
        return new AccessEventResponse(
                e.getId(),
                e.getReaderCode(),
                e.getAccessPointCode(),
                e.getCredentialId(),
                e.getDecision(),
                e.getReason(),
                e.getMessage(),
                e.getOccurredAt());
    }
}
