package com.smartkey.domain.model;

import com.smartkey.domain.enums.AccessGrantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * UMA PERMISSAO da credencial.
 *
 * Ex: a credencial do Joao tem tres:
 *   - entrada_condominio
 *   - apartamento_804
 *   - academia
 *
 * Retirar a permissao da academia = mudar o status desta linha para REVOKED.
 * Nao apagamos a linha, para o historico continuar explicavel.
 */
@Entity
@Table(
        name = "access_grants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_access_grant_credential_point",
                columnNames = {"credential_id", "access_point_code"})
)
public class AccessGrant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credential_id", nullable = false)
    private Credential credential;

    /** A porta/zona liberada, ex: "apartamento_804". */
    @Column(name = "access_point_code", nullable = false, length = 100)
    private String accessPointCode;

    /**
     * Janela propria, OPCIONAL e mais restrita que a da credencial.
     * Ex: academia liberada somente ate as 22:00.
     * Quando null, vale a janela da credencial.
     */
    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccessGrantStatus status = AccessGrantStatus.ACTIVE;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AccessGrant() {
        // exigido pelo JPA
    }

    public AccessGrant(String accessPointCode) {
        this.accessPointCode = accessPointCode;
        this.status = AccessGrantStatus.ACTIVE;
    }

    public AccessGrant(String accessPointCode, Instant validFrom, Instant validUntil) {
        this(accessPointCode);
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    /** Retira esta permissao, mantendo o registro para auditoria. */
    public void revoke() {
        this.status = AccessGrantStatus.REVOKED;
        this.revokedAt = Instant.now();
    }

    /** Devolve uma permissao que havia sido retirada. */
    public void reactivate() {
        this.status = AccessGrantStatus.ACTIVE;
        this.revokedAt = null;
    }

    public Credential getCredential() {
        return credential;
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
    }

    public String getAccessPointCode() {
        return accessPointCode;
    }

    public void setAccessPointCode(String accessPointCode) {
        this.accessPointCode = accessPointCode;
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

    public AccessGrantStatus getStatus() {
        return status;
    }

    public void setStatus(AccessGrantStatus status) {
        this.status = status;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
