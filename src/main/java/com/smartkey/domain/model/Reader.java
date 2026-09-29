package com.smartkey.domain.model;

import com.smartkey.domain.enums.ReaderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * O LEITOR (a fechadura, catraca ou totem).
 *
 * Repare na diferenca entre os dois campos de codigo:
 *   code            = QUEM e o aparelho     -> "reader_apto_804"
 *   accessPointCode = QUAL porta ele abre   -> "apartamento_804"
 *
 * Eles sao separados porque uma mesma porta pode ter dois leitores
 * (um por dentro e um por fora), e porque trocar o aparelho por defeito
 * nao deve mudar as permissoes de ninguem.
 */
@Entity
@Table(name = "readers")
public class Reader extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "access_point_code", nullable = false, length = 100)
    private String accessPointCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReaderStatus status = ReaderStatus.ACTIVE;

    /**
     * FASE 5: cada leitor tera sua propria chave para se identificar na API.
     * Guardamos apenas o hash, nunca a chave em texto puro.
     */
    @Column(name = "api_key_hash", length = 200)
    private String apiKeyHash;

    protected Reader() {
        // exigido pelo JPA
    }

    public Reader(String code, String name, String accessPointCode) {
        this.code = code;
        this.name = name;
        this.accessPointCode = accessPointCode;
        this.status = ReaderStatus.ACTIVE;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccessPointCode() {
        return accessPointCode;
    }

    public void setAccessPointCode(String accessPointCode) {
        this.accessPointCode = accessPointCode;
    }

    public ReaderStatus getStatus() {
        return status;
    }

    public void setStatus(ReaderStatus status) {
        this.status = status;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }
}
