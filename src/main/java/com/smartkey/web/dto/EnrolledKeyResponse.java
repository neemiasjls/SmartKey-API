package com.smartkey.web.dto;

import com.smartkey.domain.model.AccessGrant;
import com.smartkey.domain.model.Credential;
import com.smartkey.domain.enums.AccessGrantStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * O que o celular guarda depois de ativar a chave.
 *
 * Com estes dados mais a chave privada (que ja esta no aparelho), o celular
 * consegue gerar o QR SEM PRECISAR DE INTERNET. Quem depende de rede e o
 * leitor, que precisa conferir a assinatura e as permissoes no servidor.
 */
public record EnrolledKeyResponse(
        UUID credentialId,
        String guestName,
        String unitLabel,
        Instant validFrom,
        Instant validUntil,
        List<String> accessPoints
) {
    public static EnrolledKeyResponse from(Credential c) {
        return new EnrolledKeyResponse(
                c.getId(),
                c.getReservation().getGuest().getName(),
                c.getReservation().getUnitLabel(),
                c.getValidFrom(),
                c.getValidUntil(),
                c.getAccessGrants().stream()
                        .filter(g -> g.getStatus() == AccessGrantStatus.ACTIVE)
                        .map(AccessGrant::getAccessPointCode)
                        .toList());
    }
}
