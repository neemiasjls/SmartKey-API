package com.smartkey.domain.model;

import com.smartkey.domain.enums.DevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * O dispositivo do hospede (celular, ou navegador).
 *
 * SEGURANCA: o campo publicKey guarda apenas a chave PUBLICA.
 * A chave PRIVADA e gerada dentro do celular e NUNCA e enviada ao servidor.
 * Esse campo so sera preenchido na FASE 3.
 */
@Entity
@Table(name = "devices")
public class Device extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DevicePlatform platform = DevicePlatform.ANDROID;

    /** Nome amigavel, ex: "Samsung do Joao". */
    @Column(length = 200)
    private String label;

    /** Chave publica em Base64. Preenchida na FASE 3. */
    @Column(name = "public_key", length = 500)
    private String publicKey;

    /** Algoritmo da chave, ex: "Ed25519". Preenchido na FASE 3. */
    @Column(name = "public_key_alg", length = 50)
    private String publicKeyAlg;

    protected Device() {
        // exigido pelo JPA
    }

    public Device(Guest guest, DevicePlatform platform, String label) {
        this.guest = guest;
        this.platform = platform;
        this.label = label;
    }

    public Guest getGuest() {
        return guest;
    }

    public void setGuest(Guest guest) {
        this.guest = guest;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public void setPlatform(DevicePlatform platform) {
        this.platform = platform;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPublicKeyAlg() {
        return publicKeyAlg;
    }

    public void setPublicKeyAlg(String publicKeyAlg) {
        this.publicKeyAlg = publicKeyAlg;
    }
}
