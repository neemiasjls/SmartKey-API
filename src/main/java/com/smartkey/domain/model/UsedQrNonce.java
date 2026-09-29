package com.smartkey.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de um código QR já apresentado.
 *
 * A existência da linha É a proteção: o nonce é a chave primária, então o
 * banco recusa a segunda gravação do mesmo valor. Quem gravar recebe passagem;
 * quem tentar repetir é barrado, sem depender de nenhuma verificação no código.
 */
@Entity
@Table(name = "used_qr_nonces")
public class UsedQrNonce {

    @Id
    @Column(nullable = false, updatable = false, length = 64)
    private String nonce;

    @Column(name = "credential_id")
    private UUID credentialId;

    @Column(name = "reader_code", length = 100)
    private String readerCode;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    protected UsedQrNonce() {
        // exigido pelo JPA
    }

    public UsedQrNonce(String nonce, UUID credentialId, String readerCode,
                       Instant issuedAt, Instant usedAt) {
        this.nonce = nonce;
        this.credentialId = credentialId;
        this.readerCode = readerCode;
        this.issuedAt = issuedAt;
        this.usedAt = usedAt;
    }

    public String getNonce() {
        return nonce;
    }

    public UUID getCredentialId() {
        return credentialId;
    }

    public String getReaderCode() {
        return readerCode;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
