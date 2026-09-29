package com.smartkey.web.dto;

import com.smartkey.domain.enums.AccessDecision;
import com.smartkey.domain.enums.AccessDenyReason;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta para o leitor.
 *
 * O app leitor (FASE 5) usa "decision" para pintar a tela de verde ou
 * vermelho, e "message" para escrever o motivo embaixo.
 */
public record AccessCheckResponse(
        AccessDecision decision,
        AccessDenyReason reason,
        String message,
        String readerCode,
        String accessPointCode,
        UUID credentialId,
        Instant evaluatedAt,
        UUID eventId
) {}
