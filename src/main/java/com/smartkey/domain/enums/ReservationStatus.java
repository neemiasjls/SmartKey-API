package com.smartkey.domain.enums;

/** Situacao de uma reserva. */
public enum ReservationStatus {
    /** Reserva valida. */
    CONFIRMED,
    /** Reserva cancelada: nenhuma credencial dela deve funcionar. */
    CANCELLED
}
