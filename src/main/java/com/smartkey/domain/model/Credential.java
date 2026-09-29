package com.smartkey.domain.model;

import com.smartkey.domain.enums.CredentialStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A CHAVE DIGITAL.
 *
 * Nasce da combinacao de uma reserva com um dispositivo:
 * "o celular X pode abrir as portas da reserva Y, entre tais horarios".
 *
 * Um mesmo hospede com dois celulares tera duas credenciais, e voce pode
 * revogar apenas uma delas (ex: o celular foi roubado).
 */
@Entity
@Table(name = "credentials")
public class Credential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CredentialStatus status = CredentialStatus.ACTIVE;

    /** Inicio da validade. Normalmente igual ao check-in da reserva. */
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    /** Fim da validade. Normalmente igual ao checkout da reserva. */
    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 300)
    private String revokedReason;

    /**
     * Token de uso unico que permite ao celular do hospede registrar a sua
     * chave publica, SEM precisar da chave de administracao.
     *
     * Vai no link entregue ao hospede e queima assim que usado.
     */
    @Column(name = "enrollment_token", length = 64)
    private String enrollmentToken;

    @Column(name = "enrollment_used_at")
    private Instant enrollmentUsedAt;

    /**
     * As permissoes desta chave.
     * "orphanRemoval" nao e usado: permissoes revogadas continuam no banco
     * para fins de auditoria.
     */
    @OneToMany(mappedBy = "credential", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AccessGrant> accessGrants = new ArrayList<>();

    protected Credential() {
        // exigido pelo JPA
    }

    public Credential(Reservation reservation, Device device, Instant validFrom, Instant validUntil) {
        this.reservation = reservation;
        this.device = device;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.status = CredentialStatus.ACTIVE;
    }

    /** Revoga esta credencial imediatamente. */
    public void revoke(String reason) {
        this.status = CredentialStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public Device getDevice() {
        return device;
    }

    public CredentialStatus getStatus() {
        return status;
    }

    public void setStatus(CredentialStatus status) {
        this.status = status;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public String getEnrollmentToken() {
        return enrollmentToken;
    }

    public void setEnrollmentToken(String enrollmentToken) {
        this.enrollmentToken = enrollmentToken;
    }

    public Instant getEnrollmentUsedAt() {
        return enrollmentUsedAt;
    }

    /** Queima o token: ele nao serve mais para registrar outra chave. */
    public void markEnrollmentUsed() {
        this.enrollmentUsedAt = Instant.now();
        this.enrollmentToken = null;
    }

    public List<AccessGrant> getAccessGrants() {
        return accessGrants;
    }

    public void addAccessGrant(AccessGrant grant) {
        this.accessGrants.add(grant);
        grant.setCredential(this);
    }
}
