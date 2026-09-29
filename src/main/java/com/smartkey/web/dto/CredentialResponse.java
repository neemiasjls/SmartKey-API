package com.smartkey.web.dto;

import com.smartkey.domain.enums.CredentialStatus;
import com.smartkey.domain.model.Credential;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CredentialResponse(
        UUID id,
        UUID reservationId,
        UUID deviceId,
        String guestName,
        String unitLabel,
        CredentialStatus status,
        Instant validFrom,
        Instant validUntil,
        Instant revokedAt,
        String revokedReason,

        /**
         * Token de uso unico para o hospede ativar a chave no celular dele.
         * Fica null depois de usado.
         */
        String enrollmentToken,

        List<AccessGrantResponse> grants
) {
    public static CredentialResponse from(Credential c) {
        return new CredentialResponse(
                c.getId(),
                c.getReservation().getId(),
                c.getDevice().getId(),
                c.getReservation().getGuest().getName(),
                c.getReservation().getUnitLabel(),
                c.getStatus(),
                c.getValidFrom(),
                c.getValidUntil(),
                c.getRevokedAt(),
                c.getRevokedReason(),
                c.getEnrollmentToken(),
                c.getAccessGrants().stream().map(AccessGrantResponse::from).toList());
    }
}
