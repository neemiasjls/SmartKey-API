package com.smartkey.domain.access;

import com.smartkey.domain.enums.CredentialStatus;
import com.smartkey.domain.enums.ReservationStatus;

import java.time.Instant;

/**
 * Foto dos dados da credencial no momento da tentativa.
 *
 * @param id                 id da credencial
 * @param status             ACTIVE ou REVOKED
 * @param validFrom          inicio da validade (normalmente o check-in)
 * @param validUntil         fim da validade (normalmente o checkout)
 * @param reservationStatus  situacao da reserva que originou a credencial
 */
public record CredentialSnapshot(
        String id,
        CredentialStatus status,
        Instant validFrom,
        Instant validUntil,
        ReservationStatus reservationStatus
) {}
