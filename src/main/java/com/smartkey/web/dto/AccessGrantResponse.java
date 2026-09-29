package com.smartkey.web.dto;

import com.smartkey.domain.enums.AccessGrantStatus;
import com.smartkey.domain.model.AccessGrant;

import java.time.Instant;
import java.util.UUID;

public record AccessGrantResponse(
        UUID id,
        String accessPointCode,
        AccessGrantStatus status,
        Instant validFrom,
        Instant validUntil
) {
    public static AccessGrantResponse from(AccessGrant g) {
        return new AccessGrantResponse(
                g.getId(),
                g.getAccessPointCode(),
                g.getStatus(),
                g.getValidFrom(),
                g.getValidUntil());
    }
}
