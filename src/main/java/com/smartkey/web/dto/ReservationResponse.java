package com.smartkey.web.dto;

import com.smartkey.domain.enums.ReservationStatus;
import com.smartkey.domain.model.Reservation;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID guestId,
        String guestName,
        String unitLabel,
        Instant checkInAt,
        Instant checkOutAt,
        ReservationStatus status,
        String notes
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getGuest().getId(),
                r.getGuest().getName(),
                r.getUnitLabel(),
                r.getCheckInAt(),
                r.getCheckOutAt(),
                r.getStatus(),
                r.getNotes());
    }
}
