package com.smartkey.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Um DESAFIO: o número aleatório que o leitor manda para o celular assinar.
 *
 * O ciclo de vida é sempre o mesmo, e tem uma só direção:
 *
 *   criado  ->  usado (ou expirado)
 *
 * Uma vez usado, nunca mais volta atrás. É isso que impede o ataque de
 * repetição: gravar a resposta de um acesso legítimo e reapresentá-la depois
 * não funciona, porque aquele desafio já está queimado.
 */
@Entity
@Table(name = "challenges")
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** O numero sorteado, em Base64 (32 bytes de SecureRandom). */
    @Column(nullable = false, unique = true, length = 100, updatable = false)
    private String nonce;

    @Column(name = "reader_id", updatable = false)
    private UUID readerId;

    @Column(name = "reader_code", nullable = false, length = 100, updatable = false)
    private String readerCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Nulo enquanto o desafio nao foi usado. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_credential_id")
    private UUID usedByCredentialId;

    protected Challenge() {
        // exigido pelo JPA
    }

    public Challenge(String nonce, UUID readerId, String readerCode,
                     Instant createdAt, Instant expiresAt) {
        this.nonce = nonce;
        this.readerId = readerId;
        this.readerCode = readerCode;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getNonce() {
        return nonce;
    }

    public UUID getReaderId() {
        return readerId;
    }

    public String getReaderCode() {
        return readerCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public UUID getUsedByCredentialId() {
        return usedByCredentialId;
    }
}
