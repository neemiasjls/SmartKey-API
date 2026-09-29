package com.smartkey.domain.model;

import com.smartkey.domain.enums.AccessDecision;
import com.smartkey.domain.enums.AccessDenyReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * O HISTORICO. Toda tentativa de acesso vira uma linha aqui, tenha sido
 * liberada ou negada.
 *
 * Decisao de projeto: esta tabela NAO usa relacionamentos do JPA, apenas
 * guarda os ids e os codigos como texto solto. O motivo e que um registro de
 * auditoria deve sobreviver a tudo - se a reserva for apagada daqui a um ano,
 * o historico de quem entrou onde continua legivel.
 *
 * Esta entidade tambem nao herda de BaseEntity porque um evento nunca e
 * alterado depois de criado (nao faz sentido ter "updated_at").
 */
@Entity
@Table(name = "access_events")
public class AccessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reader_id")
    private UUID readerId;

    /** Guardado como texto: sobrevive mesmo se o leitor for removido. */
    @Column(name = "reader_code", nullable = false, length = 100)
    private String readerCode;

    @Column(name = "credential_id")
    private UUID credentialId;

    @Column(name = "access_point_code", length = 100)
    private String accessPointCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccessDecision decision;

    /** Null quando o acesso foi liberado. */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AccessDenyReason reason;

    /** Texto em portugues exibido na tela do leitor. */
    @Column(length = 300)
    private String message;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AccessEvent() {
        // exigido pelo JPA
    }

    public AccessEvent(UUID readerId,
                       String readerCode,
                       UUID credentialId,
                       String accessPointCode,
                       AccessDecision decision,
                       AccessDenyReason reason,
                       String message,
                       Instant occurredAt) {
        this.readerId = readerId;
        this.readerCode = readerCode;
        this.credentialId = credentialId;
        this.accessPointCode = accessPointCode;
        this.decision = decision;
        this.reason = reason;
        this.message = message;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReaderId() {
        return readerId;
    }

    public String getReaderCode() {
        return readerCode;
    }

    public UUID getCredentialId() {
        return credentialId;
    }

    public String getAccessPointCode() {
        return accessPointCode;
    }

    public AccessDecision getDecision() {
        return decision;
    }

    public AccessDenyReason getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
