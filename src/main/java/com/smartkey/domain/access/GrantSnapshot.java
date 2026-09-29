package com.smartkey.domain.access;

import com.smartkey.domain.enums.AccessGrantStatus;

import java.time.Instant;

/**
 * Foto de uma permissao da credencial.
 *
 * validFrom e validUntil sao OPCIONAIS (podem ser null). Servem para dar a
 * uma porta uma janela MENOR que a da credencial - por exemplo, liberar a
 * academia apenas das 06:00 as 22:00. Quando sao null, vale a janela da
 * propria credencial.
 */
public record GrantSnapshot(
        String accessPointCode,
        AccessGrantStatus status,
        Instant validFrom,
        Instant validUntil
) {
    /** Atalho para criar uma permissao simples, sem janela propria. */
    public static GrantSnapshot active(String accessPointCode) {
        return new GrantSnapshot(accessPointCode, AccessGrantStatus.ACTIVE, null, null);
    }
}
