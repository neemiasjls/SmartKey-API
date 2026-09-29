package com.smartkey.domain.model;

import com.smartkey.domain.enums.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A reserva.
 *
 * Ex: Joao, apartamento 804, de 18/09/2026 15:00 ate 21/09/2026 11:00.
 *
 * As datas sao guardadas em UTC (tipo Instant). Quem converte para o horario
 * de Brasilia e a tela, nao o banco. Isso evita erros de fuso horario.
 */
@Entity
@Table(name = "reservations")
public class Reservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    /** Identificacao da unidade, ex: "804". */
    @Column(name = "unit_label", nullable = false, length = 50)
    private String unitLabel;

    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    @Column(name = "check_out_at", nullable = false)
    private Instant checkOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(length = 500)
    private String notes;

    protected Reservation() {
        // exigido pelo JPA
    }

    public Reservation(Guest guest, String unitLabel, Instant checkInAt, Instant checkOutAt) {
        this.guest = guest;
        this.unitLabel = unitLabel;
        this.checkInAt = checkInAt;
        this.checkOutAt = checkOutAt;
        this.status = ReservationStatus.CONFIRMED;
    }

    public Guest getGuest() {
        return guest;
    }

    public void setGuest(Guest guest) {
        this.guest = guest;
    }

    public String getUnitLabel() {
        return unitLabel;
    }

    public void setUnitLabel(String unitLabel) {
        this.unitLabel = unitLabel;
    }

    public Instant getCheckInAt() {
        return checkInAt;
    }

    public void setCheckInAt(Instant checkInAt) {
        this.checkInAt = checkInAt;
    }

    public Instant getCheckOutAt() {
        return checkOutAt;
    }

    public void setCheckOutAt(Instant checkOutAt) {
        this.checkOutAt = checkOutAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
